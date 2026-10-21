-- Persist unread direct-message alerts for the launcher and expose them through Realtime.
create table if not exists public.chat_notifications (
    id uuid primary key default gen_random_uuid(),
    recipient_id uuid not null references auth.users(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    conversation_id uuid not null references public.chat_conversations(id) on delete cascade,
    message_id uuid not null unique references public.chat_messages(id) on delete cascade,
    message_preview text not null check (char_length(message_preview) <= 180),
    created_at timestamptz not null default now(),
    read_at timestamptz
);

create index if not exists chat_notifications_recipient_unread
    on public.chat_notifications (recipient_id, created_at desc)
    where read_at is null;

alter table public.chat_notifications enable row level security;

drop policy if exists chat_notifications_recipient_read on public.chat_notifications;
create policy chat_notifications_recipient_read
    on public.chat_notifications for select to authenticated
    using (recipient_id = auth.uid());

revoke all on table public.chat_notifications from public, anon, authenticated;
grant select on public.chat_notifications to authenticated;

create or replace function public.create_chat_notification_for_message()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    recipient_user_id uuid;
begin
    select case
        when c.user_a = new.sender_id then c.user_b
        when c.user_b = new.sender_id then c.user_a
        else null
    end
      into recipient_user_id
      from public.chat_conversations c
     where c.id = new.conversation_id;

    if recipient_user_id is null then
        return new;
    end if;

    insert into public.chat_notifications (
        recipient_id,
        sender_id,
        conversation_id,
        message_id,
        message_preview
    ) values (
        recipient_user_id,
        new.sender_id,
        new.conversation_id,
        new.id,
        left(new.body, 180)
    )
    on conflict (message_id) do nothing;

    return new;
end;
$$;

drop trigger if exists chat_messages_create_notification on public.chat_messages;
create trigger chat_messages_create_notification
after insert on public.chat_messages
for each row execute function public.create_chat_notification_for_message();

create or replace function public.mark_chat_conversation_notifications_read(p_conversation_id uuid)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    updated_count integer;
begin
    if current_user_id is null then
        raise exception 'Sign in to update chat notifications.' using errcode = '28000';
    end if;

    if not exists (
        select 1
          from public.chat_conversations c
         where c.id = p_conversation_id
           and (c.user_a = current_user_id or c.user_b = current_user_id)
    ) then
        raise exception 'Conversation not found.' using errcode = '42501';
    end if;

    update public.chat_notifications
       set read_at = now()
     where recipient_id = current_user_id
       and conversation_id = p_conversation_id
       and read_at is null;

    get diagnostics updated_count = row_count;
    return updated_count;
end;
$$;

revoke all on function public.mark_chat_conversation_notifications_read(uuid) from public, anon;
grant execute on function public.mark_chat_conversation_notifications_read(uuid) to authenticated;

-- The unauthenticated account-session Edge Function uses this bounded IP counter before
-- provisioning identities. It stores only a one-way HMAC of the source IP, never the IP itself.
create table if not exists public.chat_auth_rate_limits (
    ip_hash text not null,
    created_at timestamptz not null default now()
);
create index if not exists chat_auth_rate_limits_ip_time
    on public.chat_auth_rate_limits (ip_hash, created_at desc);
alter table public.chat_auth_rate_limits enable row level security;
revoke all on table public.chat_auth_rate_limits from public, anon, authenticated;

create or replace function public.take_chat_auth_rate_limit(p_ip_hash text)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    used_count integer;
begin
    if p_ip_hash is null or p_ip_hash !~ '^[0-9a-f]{64}$' then
        return false;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(p_ip_hash, 0));
    delete from public.chat_auth_rate_limits
     where created_at < now() - interval '24 hours';

    select count(*)
      into used_count
      from public.chat_auth_rate_limits
     where ip_hash = p_ip_hash
       and created_at >= now() - interval '1 hour';

    if used_count >= 100 then
        return false;
    end if;

    insert into public.chat_auth_rate_limits (ip_hash) values (p_ip_hash);
    return true;
end;
$$;

revoke all on function public.take_chat_auth_rate_limit(text) from public, anon, authenticated;
grant execute on function public.take_chat_auth_rate_limit(text) to service_role;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
       AND NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
             WHERE pubname = 'supabase_realtime'
               AND schemaname = 'public'
               AND tablename = 'chat_notifications'
       ) THEN
        EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_notifications';
    END IF;
END $$;
