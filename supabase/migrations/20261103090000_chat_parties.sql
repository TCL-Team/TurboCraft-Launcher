-- Parties ("play together") for the launcher social system.
-- All direct table access is protected by RLS; state changes go through checked RPCs.
-- Client-side code only uses the Supabase publishable key, never a service-role key.

create table if not exists public.chat_parties (
    id uuid primary key default gen_random_uuid(),
    host_id uuid not null references public.chat_profiles(id) on delete cascade,
    title text not null,
    version_id text,
    instance_name text,
    mc_version text,
    loader text,
    modpack_source text,
    modpack_id text,
    modpack_icon_url text,
    server_address text,
    max_members integer not null default 4,
    is_public boolean not null default false,
    invite_code text not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '12 hours',
    constraint chat_parties_title_format check (char_length(btrim(title)) between 1 and 48),
    constraint chat_parties_capacity check (max_members between 2 and 16)
);
create index if not exists chat_parties_public_live
    on public.chat_parties (created_at desc)
    where is_public;
create index if not exists chat_parties_host on public.chat_parties (host_id);

create table if not exists public.chat_party_members (
    party_id uuid not null references public.chat_parties(id) on delete cascade,
    user_id uuid not null references public.chat_profiles(id) on delete cascade,
    is_host boolean not null default false,
    joined_at timestamptz not null default now(),
    constraint chat_party_members_primary_key primary key (party_id, user_id)
);
create index if not exists chat_party_members_user on public.chat_party_members (user_id);

create table if not exists public.chat_party_invites (
    id uuid primary key default gen_random_uuid(),
    party_id uuid not null references public.chat_parties(id) on delete cascade,
    from_id uuid not null references public.chat_profiles(id) on delete cascade,
    to_id uuid not null references public.chat_profiles(id) on delete cascade,
    status text not null default 'pending'
        check (status in ('pending', 'accepted', 'declined', 'cancelled')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    constraint chat_party_invites_not_self check (from_id <> to_id)
);
create unique index if not exists chat_party_invites_one_pending
    on public.chat_party_invites (party_id, to_id)
    where status = 'pending';
create index if not exists chat_party_invites_to_pending
    on public.chat_party_invites (to_id, created_at desc)
    where status = 'pending';

create table if not exists public.chat_party_messages (
    id uuid primary key default gen_random_uuid(),
    party_id uuid not null references public.chat_parties(id) on delete cascade,
    sender_id uuid not null references public.chat_profiles(id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);
create index if not exists chat_party_messages_party_created
    on public.chat_party_messages (party_id, created_at desc);

alter table public.chat_parties enable row level security;
alter table public.chat_party_members enable row level security;
alter table public.chat_party_invites enable row level security;
alter table public.chat_party_messages enable row level security;

-- Membership checks run through a SECURITY DEFINER helper so the policies on
-- chat_party_members can look at the table without recursing into themselves.
create or replace function public.chat_is_party_member(p_party_id uuid, p_user_id uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1 from public.chat_party_members m
        where m.party_id = p_party_id and m.user_id = p_user_id
    );
$$;

revoke all on function public.chat_is_party_member(uuid, uuid) from public, anon;
grant execute on function public.chat_is_party_member(uuid, uuid) to authenticated;

drop policy if exists chat_parties_visible on public.chat_parties;
create policy chat_parties_visible
    on public.chat_parties for select to authenticated
    using (
        is_public
        or host_id = auth.uid()
        or public.chat_is_party_member(id)
        or exists (
            select 1 from public.chat_party_invites i
            where i.party_id = chat_parties.id and i.to_id = auth.uid() and i.status = 'pending'
        )
    );

drop policy if exists chat_party_members_visible on public.chat_party_members;
create policy chat_party_members_visible
    on public.chat_party_members for select to authenticated
    using (
        user_id = auth.uid()
        or public.chat_is_party_member(party_id)
        or exists (
            select 1 from public.chat_parties p
            where p.id = chat_party_members.party_id and p.is_public and p.expires_at > now()
        )
    );

drop policy if exists chat_party_invites_participant on public.chat_party_invites;
create policy chat_party_invites_participant
    on public.chat_party_invites for select to authenticated
    using (from_id = auth.uid() or to_id = auth.uid());

drop policy if exists chat_party_messages_member_read on public.chat_party_messages;
create policy chat_party_messages_member_read
    on public.chat_party_messages for select to authenticated
    using (public.chat_is_party_member(party_id));

drop policy if exists chat_party_messages_member_insert on public.chat_party_messages;
create policy chat_party_messages_member_insert
    on public.chat_party_messages for insert to authenticated
    with check (sender_id = auth.uid() and public.chat_is_party_member(party_id));

drop policy if exists chat_party_messages_sender_delete on public.chat_party_messages;
create policy chat_party_messages_sender_delete
    on public.chat_party_messages for delete to authenticated
    using (sender_id = auth.uid());

-- ---------------------------------------------------------------- helpers

create or replace function public.chat_party_require_member(p_party_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if not exists (
        select 1 from public.chat_party_members m
        where m.party_id = p_party_id and m.user_id = auth.uid()
    ) then
        raise exception 'You are not in this party.' using errcode = '42501';
    end if;
end;
$$;

create or replace function public.chat_party_generate_code()
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    code text;
    i integer;
begin
    loop
        code := '';
        for i in 1..6 loop
            code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
        end loop;
        exit when not exists (select 1 from public.chat_parties p where p.invite_code = code);
    end loop;
    return code;
end;
$$;

-- ---------------------------------------------------------------- RPCs

create or replace function public.create_chat_party(
    p_title text,
    p_version_id text default null,
    p_instance_name text default null,
    p_mc_version text default null,
    p_loader text default null,
    p_server_address text default null,
    p_max_members integer default 4,
    p_is_public boolean default false,
    p_modpack_source text default null,
    p_modpack_id text default null,
    p_modpack_icon_url text default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    created_party_id uuid;
    clean_title text := btrim(coalesce(p_title, ''));
    capacity integer := least(greatest(coalesce(p_max_members, 4), 2), 16);
begin
    if current_user_id is null then
        raise exception 'Sign in to start a party.' using errcode = '28000';
    end if;
    if char_length(clean_title) < 1 then
        raise exception 'Give your party a name.';
    end if;
    if not exists (select 1 from public.chat_profiles p where p.id = current_user_id) then
        raise exception 'Create your chat profile first.';
    end if;

    -- A host can only own one live party; quietly drop expired ones first.
    delete from public.chat_parties p
     where p.host_id = current_user_id and p.expires_at <= now();

    if exists (select 1 from public.chat_parties p where p.host_id = current_user_id) then
        raise exception 'You are already hosting a party. End it before creating another.';
    end if;

    insert into public.chat_parties (
        host_id, title, version_id, instance_name, mc_version, loader,
        modpack_source, modpack_id, modpack_icon_url, server_address,
        max_members, is_public, invite_code
    ) values (
        current_user_id, clean_title, p_version_id, p_instance_name, p_mc_version, p_loader,
        p_modpack_source, p_modpack_id, p_modpack_icon_url, p_server_address,
        capacity, coalesce(p_is_public, false), public.chat_party_generate_code()
    )
    returning id into created_party_id;

    insert into public.chat_party_members (party_id, user_id, is_host)
    values (created_party_id, current_user_id, true)
    on conflict (party_id, user_id) do update set is_host = true;

    return created_party_id;
end;
$$;

create or replace function public.join_chat_party_by_code(p_code text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    party_row public.chat_parties%rowtype;
    member_count integer;
begin
    if auth.uid() is null then
        raise exception 'Sign in to join a party.' using errcode = '28000';
    end if;

    select * into party_row
      from public.chat_parties p
     where p.invite_code = upper(btrim(coalesce(p_code, '')))
     for update;
    if not found then
        raise exception 'That party code is not valid anymore.';
    end if;
    if party_row.expires_at <= now() then
        raise exception 'That party has ended.';
    end if;

    if exists (
        select 1 from public.chat_party_members m
        where m.party_id = party_row.id and m.user_id = auth.uid()
    ) then
        return party_row.id;
    end if;

    select count(*) into member_count
      from public.chat_party_members m where m.party_id = party_row.id;
    if member_count >= party_row.max_members then
        raise exception 'This party is full.';
    end if;

    insert into public.chat_party_members (party_id, user_id, is_host)
    values (party_row.id, auth.uid(), false);

    update public.chat_party_invites i
       set status = 'accepted', responded_at = now()
     where i.party_id = party_row.id and i.to_id = auth.uid() and i.status = 'pending';

    return party_row.id;
end;
$$;

create or replace function public.join_chat_party(p_party_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    party_row public.chat_parties%rowtype;
    member_count integer;
begin
    if auth.uid() is null then
        raise exception 'Sign in to join a party.' using errcode = '28000';
    end if;

    select * into party_row from public.chat_parties p where p.id = p_party_id for update;
    if not found then
        raise exception 'This party no longer exists.';
    end if;
    if party_row.expires_at <= now() then
        raise exception 'That party has ended.';
    end if;
    if not party_row.is_public and not exists (
        select 1 from public.chat_party_invites i
        where i.party_id = party_row.id and i.to_id = auth.uid() and i.status = 'pending'
    ) then
        raise exception 'This party is private. Ask the host for an invite.' using errcode = '42501';
    end if;

    if exists (
        select 1 from public.chat_party_members m
        where m.party_id = party_row.id and m.user_id = auth.uid()
    ) then
        return;
    end if;

    select count(*) into member_count
      from public.chat_party_members m where m.party_id = party_row.id;
    if member_count >= party_row.max_members then
        raise exception 'This party is full.';
    end if;

    insert into public.chat_party_members (party_id, user_id, is_host)
    values (party_row.id, auth.uid(), false);

    update public.chat_party_invites i
       set status = 'accepted', responded_at = now()
     where i.party_id = party_row.id and i.to_id = auth.uid() and i.status = 'pending';
end;
$$;

create or replace function public.leave_chat_party(p_party_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    party_row public.chat_parties%rowtype;
    next_host uuid;
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;

    select * into party_row from public.chat_parties p where p.id = p_party_id for update;
    if not found then
        return;
    end if;

    delete from public.chat_party_members m
     where m.party_id = p_party_id and m.user_id = auth.uid();

    if not exists (select 1 from public.chat_party_members m where m.party_id = p_party_id) then
        delete from public.chat_parties p where p.id = p_party_id;
        return;
    end if;

    if party_row.host_id = auth.uid() then
        select m.user_id into next_host
          from public.chat_party_members m
         where m.party_id = p_party_id
         order by m.joined_at asc
         limit 1;

        update public.chat_party_members m
           set is_host = (m.user_id = next_host)
         where m.party_id = p_party_id;

        update public.chat_parties p
           set host_id = next_host, updated_at = now()
         where p.id = p_party_id;
    end if;
end;
$$;

create or replace function public.delete_chat_party(p_party_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;
    if not exists (
        select 1 from public.chat_parties p where p.id = p_party_id and p.host_id = auth.uid()
    ) then
        raise exception 'Only the host can end this party.' using errcode = '42501';
    end if;
    delete from public.chat_parties p where p.id = p_party_id;
end;
$$;

create or replace function public.refresh_chat_party(p_party_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;
    update public.chat_parties p
       set expires_at = greatest(p.expires_at, now()) + interval '60 minutes',
           updated_at = now()
     where p.id = p_party_id
       and exists (
           select 1 from public.chat_party_members m
           where m.party_id = p.id and m.user_id = auth.uid()
       );
    if not found then
        raise exception 'You are not in this party.' using errcode = '42501';
    end if;
end;
$$;

create or replace function public.invite_to_chat_party(p_party_id uuid, p_username text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    target_id uuid;
    invite_id uuid;
    member_count integer;
    party_row public.chat_parties%rowtype;
    normalized text := lower(trim(both '@' from btrim(coalesce(p_username, ''))));
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;
    perform public.chat_party_require_member(p_party_id);

    select * into party_row from public.chat_parties p where p.id = p_party_id;
    if not found or party_row.expires_at <= now() then
        raise exception 'This party has ended.';
    end if;

    select p.id into target_id from public.chat_profiles p where p.username = normalized;
    if target_id is null then
        raise exception 'No player found with that username.';
    end if;
    if target_id = auth.uid() then
        raise exception 'You are already in this party.';
    end if;
    if exists (
        select 1 from public.chat_party_members m
        where m.party_id = p_party_id and m.user_id = target_id
    ) then
        raise exception 'They are already in this party.';
    end if;
    if not exists (
        select 1 from public.chat_friendships f
        where (f.user_a = least(auth.uid(), target_id) and f.user_b = greatest(auth.uid(), target_id))
    ) then
        raise exception 'You can only invite friends. Add them first.';
    end if;

    select count(*) into member_count from public.chat_party_members m where m.party_id = p_party_id;
    if member_count >= party_row.max_members then
        raise exception 'This party is full.';
    end if;

    select i.id into invite_id
      from public.chat_party_invites i
     where i.party_id = p_party_id and i.to_id = target_id and i.status = 'pending';
    if invite_id is not null then
        return invite_id;
    end if;

    insert into public.chat_party_invites (party_id, from_id, to_id)
    values (p_party_id, auth.uid(), target_id)
    returning id into invite_id;

    return invite_id;
end;
$$;

create or replace function public.respond_chat_party_invite(p_invite_id uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    invite_row public.chat_party_invites%rowtype;
    party_row public.chat_parties%rowtype;
    member_count integer;
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;

    select * into invite_row from public.chat_party_invites i where i.id = p_invite_id for update;
    if not found then
        raise exception 'Invite not found.';
    end if;
    if invite_row.to_id <> auth.uid() then
        raise exception 'Only the invited player can respond.' using errcode = '42501';
    end if;
    if invite_row.status <> 'pending' then
        raise exception 'This invite was already handled.';
    end if;

    if not p_accept then
        update public.chat_party_invites i
           set status = 'declined', responded_at = now()
         where i.id = invite_row.id;
        return;
    end if;

    select * into party_row from public.chat_parties p where p.id = invite_row.party_id;
    if not found or party_row.expires_at <= now() then
        update public.chat_party_invites i
           set status = 'declined', responded_at = now()
         where i.id = invite_row.id;
        raise exception 'That party has already ended.';
    end if;

    select count(*) into member_count
      from public.chat_party_members m where m.party_id = party_row.id;
    if member_count >= party_row.max_members then
        raise exception 'This party is full.';
    end if;

    insert into public.chat_party_members (party_id, user_id, is_host)
    values (party_row.id, auth.uid(), false)
    on conflict (party_id, user_id) do nothing;

    update public.chat_party_invites i
       set status = 'accepted', responded_at = now()
     where i.id = invite_row.id;
end;
$$;

create or replace function public.kick_chat_party_member(p_party_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if auth.uid() is null then
        raise exception 'Sign in first.' using errcode = '28000';
    end if;
    if not exists (
        select 1 from public.chat_parties p where p.id = p_party_id and p.host_id = auth.uid()
    ) then
        raise exception 'Only the host can remove members.' using errcode = '42501';
    end if;
    delete from public.chat_party_members m
     where m.party_id = p_party_id and m.user_id = p_user_id and m.is_host = false;
end;
$$;

create or replace function public.touch_chat_party()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.chat_parties p set updated_at = now() where p.id = new.party_id;
    return new;
end;
$$;

drop trigger if exists chat_party_messages_touch on public.chat_party_messages;
create trigger chat_party_messages_touch
    after insert on public.chat_party_messages
    for each row execute function public.touch_chat_party();

-- ---------------------------------------------------------------- grants

revoke all on function public.create_chat_party(text, text, text, text, text, text, integer, boolean, text, text, text) from public, anon;
revoke all on function public.join_chat_party_by_code(text) from public, anon;
revoke all on function public.join_chat_party(uuid) from public, anon;
revoke all on function public.leave_chat_party(uuid) from public, anon;
revoke all on function public.delete_chat_party(uuid) from public, anon;
revoke all on function public.refresh_chat_party(uuid) from public, anon;
revoke all on function public.invite_to_chat_party(uuid, text) from public, anon;
revoke all on function public.respond_chat_party_invite(uuid, boolean) from public, anon;
revoke all on function public.kick_chat_party_member(uuid, uuid) from public, anon;

grant execute on function public.create_chat_party(text, text, text, text, text, text, integer, boolean, text, text, text) to authenticated;
grant execute on function public.join_chat_party_by_code(text) to authenticated;
grant execute on function public.join_chat_party(uuid) to authenticated;
grant execute on function public.leave_chat_party(uuid) to authenticated;
grant execute on function public.delete_chat_party(uuid) to authenticated;
grant execute on function public.refresh_chat_party(uuid) to authenticated;
grant execute on function public.invite_to_chat_party(uuid, text) to authenticated;
grant execute on function public.respond_chat_party_invite(uuid, boolean) to authenticated;
grant execute on function public.kick_chat_party_member(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------- realtime

alter table public.chat_party_messages replica identity full;
alter table public.chat_party_invites replica identity full;
alter table public.chat_party_members replica identity full;

do $$
declare
    table_to_add text;
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        foreach table_to_add in array array['chat_party_messages', 'chat_party_invites', 'chat_party_members'] loop
            if not exists (
                select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime'
                   and schemaname = 'public'
                   and tablename = table_to_add
            ) then
                execute format('alter publication supabase_realtime add table public.%I', table_to_add);
            end if;
        end loop;
    end if;
end;
$$;
