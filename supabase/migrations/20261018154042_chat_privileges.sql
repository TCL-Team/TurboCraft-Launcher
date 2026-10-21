-- Grant only the operations used by the authenticated chat client; RLS further limits rows.
grant usage on schema public to authenticated;
grant select on public.chat_profiles to authenticated;
grant insert, update on public.chat_profiles to authenticated;
grant select on public.chat_conversations to authenticated;
grant select on public.chat_friendships to authenticated;
grant select on public.chat_friend_requests to authenticated;
grant select, insert on public.chat_messages to authenticated;
