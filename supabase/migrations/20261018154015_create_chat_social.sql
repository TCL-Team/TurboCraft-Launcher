-- Social chat for the launcher. All direct table access is protected by RLS;
-- client-side code only uses the Supabase publishable key, never a service-role key.

create table if not exists public.chat_profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text not null,
    display_name text not null,
    created_at timestamptz not null default now(),
    constraint chat_profiles_username_format check (
        username = lower(username) and username ~ '^[a-z0-9_.]{3,20}$'
    )
);
create unique index if not exists chat_profiles_username_unique
    on public.chat_profiles (username);

create table if not exists public.chat_conversations (
    id uuid primary key default gen_random_uuid(),
    user_a uuid not null references auth.users(id) on delete cascade,
    user_b uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chat_conversations_ordered_pair check (user_a < user_b),
    constraint chat_conversations_unique_pair unique (user_a, user_b)
);

create table if not exists public.chat_friendships (
    user_a uuid not null references auth.users(id) on delete cascade,
    user_b uuid not null references auth.users(id) on delete cascade,
    conversation_id uuid not null unique references public.chat_conversations(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint chat_friendships_ordered_pair check (user_a < user_b),
    constraint chat_friendships_primary_key primary key (user_a, user_b)
);

create table if not exists public.chat_friend_requests (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references auth.users(id) on delete cascade,
    receiver_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'pending'
        check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    constraint chat_friend_requests_not_self check (sender_id <> receiver_id)
);
create unique index if not exists chat_friend_requests_one_pending_direction
    on public.chat_friend_requests (sender_id, receiver_id)
    where status = 'pending';
create index if not exists chat_friend_requests_receiver_pending
    on public.chat_friend_requests (receiver_id, created_at desc)
    where status = 'pending';

create table if not exists public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.chat_conversations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);
create index if not exists chat_messages_conversation_created
    on public.chat_messages (conversation_id, created_at desc);

alter table public.chat_profiles enable row level security;
alter table public.chat_conversations enable row level security;
alter table public.chat_friendships enable row level security;
alter table public.chat_friend_requests enable row level security;
alter table public.chat_messages enable row level security;

-- Usernames are searchable by signed-in chat users; each account can only edit its own profile.
drop policy if exists chat_profiles_authenticated_read on public.chat_profiles;
create policy chat_profiles_authenticated_read
    on public.chat_profiles for select to authenticated
    using (auth.uid() is not null);
drop policy if exists chat_profiles_insert_self on public.chat_profiles;
create policy chat_profiles_insert_self
    on public.chat_profiles for insert to authenticated
    with check (id = auth.uid());
drop policy if exists chat_profiles_update_self on public.chat_profiles;
create policy chat_profiles_update_self
    on public.chat_profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

-- Conversation and friendship rows are visible only to their two members.
drop policy if exists chat_conversations_member_read on public.chat_conversations;
create policy chat_conversations_member_read
    on public.chat_conversations for select to authenticated
    using (auth.uid() = user_a or auth.uid() = user_b);
drop policy if exists chat_friendships_member_read on public.chat_friendships;
create policy chat_friendships_member_read
    on public.chat_friendships for select to authenticated
    using (auth.uid() = user_a or auth.uid() = user_b);

-- Requests are visible to their sender and recipient; changes happen through checked RPCs only.
drop policy if exists chat_friend_requests_participant_read on public.chat_friend_requests;
create policy chat_friend_requests_participant_read
    on public.chat_friend_requests for select to authenticated
    using (auth.uid() = sender_id or auth.uid() = receiver_id);

-- Message history is visible to the two members; messages are immutable after insert.
drop policy if exists chat_messages_member_read on public.chat_messages;
create policy chat_messages_member_read
    on public.chat_messages for select to authenticated
    using (
        exists (
            select 1 from public.chat_conversations c
            where c.id = chat_messages.conversation_id
              and (auth.uid() = c.user_a or auth.uid() = c.user_b)
        )
    );
drop policy if exists chat_messages_member_insert on public.chat_messages;
create policy chat_messages_member_insert
    on public.chat_messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.chat_conversations c
            where c.id = chat_messages.conversation_id
              and (auth.uid() = c.user_a or auth.uid() = c.user_b)
        )
    );

create or replace function public.send_chat_friend_request(target_username text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    target_user_id uuid;
    request_id uuid;
    normalized_username text := lower(trim(both '@' from btrim(target_username)));
begin
    if current_user_id is null then
        raise exception 'Sign in to send friend requests.' using errcode = '28000';
    end if;

    if normalized_username !~ '^[a-z0-9_.]{3,20}$' then
        raise exception 'Enter a valid username (3-20 letters, numbers, dots or underscores).';
    end if;

    select id into target_user_id
      from public.chat_profiles
     where username = normalized_username;
    if target_user_id is null then
        raise exception 'No chat user found with that username.';
    end if;
    if target_user_id = current_user_id then
        raise exception 'You cannot add yourself as a friend.';
    end if;

    if exists (
        select 1 from public.chat_friendships f
         where f.user_a = least(current_user_id, target_user_id)
           and f.user_b = greatest(current_user_id, target_user_id)
    ) then
        raise exception 'You are already friends.';
    end if;

    if exists (
        select 1 from public.chat_friend_requests r
         where r.sender_id = target_user_id
           and r.receiver_id = current_user_id
           and r.status = 'pending'
    ) then
        raise exception 'This user has already sent you a request. Accept it from Requests.';
    end if;

    select id into request_id
      from public.chat_friend_requests
     where sender_id = current_user_id
       and receiver_id = target_user_id
       and status = 'pending';
    if request_id is not null then
        return request_id;
    end if;

    insert into public.chat_friend_requests (sender_id, receiver_id)
    values (current_user_id, target_user_id)
    returning id into request_id;
    return request_id;
end;
$$;

create or replace function public.respond_chat_friend_request(request_id uuid, accept boolean)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    request_row public.chat_friend_requests%rowtype;
    low_user_id uuid;
    high_user_id uuid;
    conversation_uuid uuid;
begin
    if current_user_id is null then
        raise exception 'Sign in to respond to friend requests.' using errcode = '28000';
    end if;

    select * into request_row
      from public.chat_friend_requests
     where id = request_id
     for update;
    if not found then
        raise exception 'Friend request not found.';
    end if;
    if request_row.receiver_id <> current_user_id then
        raise exception 'Only the recipient can respond to this request.' using errcode = '42501';
    end if;
    if request_row.status <> 'pending' then
        raise exception 'This friend request has already been handled.';
    end if;

    if not accept then
        update public.chat_friend_requests
           set status = 'rejected', responded_at = now()
         where id = request_row.id;
        return;
    end if;

    update public.chat_friend_requests
       set status = 'accepted', responded_at = now()
     where id = request_row.id;

    low_user_id := least(request_row.sender_id, request_row.receiver_id);
    high_user_id := greatest(request_row.sender_id, request_row.receiver_id);

    insert into public.chat_conversations (user_a, user_b)
    values (low_user_id, high_user_id)
    on conflict (user_a, user_b) do nothing;

    select id into conversation_uuid
      from public.chat_conversations
     where user_a = low_user_id and user_b = high_user_id;

    insert into public.chat_friendships (user_a, user_b, conversation_id)
    values (low_user_id, high_user_id, conversation_uuid)
    on conflict (user_a, user_b) do nothing;
end;
$$;

revoke all on function public.send_chat_friend_request(text) from public, anon;
grant execute on function public.send_chat_friend_request(text) to authenticated;
revoke all on function public.respond_chat_friend_request(uuid, boolean) from public, anon;
grant execute on function public.respond_chat_friend_request(uuid, boolean) to authenticated;

create or replace function public.touch_chat_conversation()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.chat_conversations
       set updated_at = new.created_at
     where id = new.conversation_id;
    return new;
end;
$$;

drop trigger if exists chat_messages_touch_conversation on public.chat_messages;
create trigger chat_messages_touch_conversation
    after insert on public.chat_messages
    for each row execute function public.touch_chat_conversation();

alter table public.chat_friend_requests replica identity full;
alter table public.chat_messages replica identity full;

do $$
declare
    table_to_add text;
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        foreach table_to_add in array array['chat_messages', 'chat_friend_requests'] loop
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
