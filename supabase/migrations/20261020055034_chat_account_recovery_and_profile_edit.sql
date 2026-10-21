-- Add portable recovery for chat profiles and editable profile bios.
-- Recovery secrets and launcher identity links are only accessible to service_role.

alter table public.chat_profiles
    add column if not exists bio text not null default '';

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'chat_profiles_bio_length'
           and conrelid = 'public.chat_profiles'::regclass
    ) then
        alter table public.chat_profiles
            add constraint chat_profiles_bio_length check (char_length(bio) <= 160);
    end if;
end $$;

create table if not exists public.chat_recovery_credentials (
    auth_user_id uuid primary key references auth.users(id) on delete cascade,
    recovery_hash text not null check (recovery_hash ~ '^[0-9a-f]{64}$'),
    updated_at timestamptz not null default now()
);
alter table public.chat_recovery_credentials enable row level security;
revoke all on table public.chat_recovery_credentials from public, anon, authenticated;

-- A verified recovery code can link a newly-created launcher account to the existing chat user.
create table if not exists public.chat_launcher_identities (
    identity_type text not null check (identity_type in ('microsoft', 'offline')),
    identity_id text not null,
    auth_user_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (identity_type, identity_id),
    constraint chat_launcher_identities_identity_format check (
        (identity_type = 'microsoft' and identity_id ~ '^[0-9a-f]{32}$')
        or
        (identity_type = 'offline' and identity_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
    )
);
create index if not exists chat_launcher_identities_user
    on public.chat_launcher_identities (auth_user_id);
alter table public.chat_launcher_identities enable row level security;
revoke all on table public.chat_launcher_identities from public, anon, authenticated;

-- Store only HMACs of IP and username for a short-lived recovery throttle.
create table if not exists public.chat_recovery_attempts (
    ip_hash text not null check (ip_hash ~ '^[0-9a-f]{64}$'),
    username_hash text not null check (username_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null default now()
);
create index if not exists chat_recovery_attempts_ip_time
    on public.chat_recovery_attempts (ip_hash, created_at desc);
create index if not exists chat_recovery_attempts_username_time
    on public.chat_recovery_attempts (username_hash, created_at desc);
alter table public.chat_recovery_attempts enable row level security;
revoke all on table public.chat_recovery_attempts from public, anon, authenticated;

create or replace function public.take_chat_recovery_rate_limit(
    p_ip_hash text,
    p_username_hash text
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    ip_attempts integer;
    username_attempts integer;
begin
    if p_ip_hash is null or p_ip_hash !~ '^[0-9a-f]{64}$'
       or p_username_hash is null or p_username_hash !~ '^[0-9a-f]{64}$' then
        return false;
    end if;

    perform pg_advisory_xact_lock(hashtextextended('chat-recovery-ip:' || p_ip_hash, 0));
    perform pg_advisory_xact_lock(hashtextextended('chat-recovery-user:' || p_username_hash, 0));

    delete from public.chat_recovery_attempts
     where created_at < now() - interval '24 hours';

    select count(*) into ip_attempts
      from public.chat_recovery_attempts
     where ip_hash = p_ip_hash
       and created_at >= now() - interval '1 hour';

    select count(*) into username_attempts
      from public.chat_recovery_attempts
     where username_hash = p_username_hash
       and created_at >= now() - interval '1 hour';

    if ip_attempts >= 20 or username_attempts >= 10 then
        return false;
    end if;

    insert into public.chat_recovery_attempts (ip_hash, username_hash)
    values (p_ip_hash, p_username_hash);
    return true;
end;
$$;

revoke all on function public.take_chat_recovery_rate_limit(text, text) from public, anon, authenticated;
grant execute on function public.take_chat_recovery_rate_limit(text, text) to service_role;
