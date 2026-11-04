-- Robust chat profile bootstrap.
--
-- The client used to POST straight into chat_profiles. Any hiccup there (stale chat
-- session linked to a deleted auth user, a username race, a policy edge case) left the
-- "Claim your social handle" screen stuck with no useful message. This RPC is the single
-- entry point for creating/refreshing a profile: it is idempotent, raises readable errors
-- and always returns the profile row.

create or replace function public.ensure_chat_profile(
    p_username text,
    p_display_name text default null
)
returns public.chat_profiles
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    profile_row public.chat_profiles%rowtype;
    clean_username text := lower(trim(both '@' from btrim(coalesce(p_username, ''))));
    clean_display text := nullif(btrim(coalesce(p_display_name, '')), '');
begin
    if current_user_id is null then
        raise exception 'Sign in with a launcher account first.' using errcode = '28000';
    end if;

    -- Already has a profile: refresh presence and hand it back (idempotent).
    select * into profile_row from public.chat_profiles p where p.id = current_user_id;
    if found then
        update public.chat_profiles p
           set last_active_at = now()
         where p.id = current_user_id
        returning * into profile_row;
        return profile_row;
    end if;

    if clean_username !~ '^[a-z0-9_.]{3,20}$' then
        raise exception 'Username must be 3-20 characters: letters, numbers, dot or underscore.';
    end if;

    begin
        insert into public.chat_profiles (id, username, display_name, bio, last_active_at)
        values (
            current_user_id,
            clean_username,
            coalesce(left(clean_display, 32), clean_username),
            '',
            now()
        )
        returning * into profile_row;
    exception
        when unique_violation then
            raise exception 'That handle is already taken. Try another one.';
        when foreign_key_violation then
            raise exception 'Your chat session expired. Reopen Social to reconnect.' using errcode = '28000';
    end;

    return profile_row;
end;
$$;

revoke all on function public.ensure_chat_profile(text, text) from public, anon;
grant execute on function public.ensure_chat_profile(text, text) to authenticated;

-- A tiny self-check the client can call to render useful diagnostics instead of a
-- silent failure: which auth user the session maps to, whether a profile exists and
-- how many friends/parties the account can see.
create or replace function public.chat_self_check()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_user_id uuid := auth.uid();
    profile_row public.chat_profiles%rowtype;
begin
    if current_user_id is null then
        return jsonb_build_object('authenticated', false);
    end if;

    select * into profile_row from public.chat_profiles p where p.id = current_user_id;

    return jsonb_build_object(
        'authenticated', true,
        'user_id', current_user_id,
        'has_profile', found,
        'username', profile_row.username,
        'display_name', profile_row.display_name,
        'friends', (
            select count(*) from public.chat_friendships f
             where f.user_a = current_user_id or f.user_b = current_user_id
        ),
        'pending_requests', (
            select count(*) from public.chat_friend_requests r
             where r.receiver_id = current_user_id and r.status = 'pending'
        ),
        'parties', (
            select count(*) from public.chat_party_members m where m.user_id = current_user_id
        ),
        'server_time', now()
    );
end;
$$;

revoke all on function public.chat_self_check() from public, anon;
grant execute on function public.chat_self_check() to authenticated;
