-- Remove unread/inbox entries when their message is deleted by its author.
CREATE OR REPLACE FUNCTION public.cleanup_chat_notifications_for_deleted_message()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    DELETE FROM public.chat_notifications
     WHERE message_id = OLD.id;
    RETURN OLD;
END;
$$;

REVOKE ALL ON FUNCTION public.cleanup_chat_notifications_for_deleted_message() FROM public, anon, authenticated;

DROP TRIGGER IF EXISTS chat_messages_cleanup_notifications_after_delete ON public.chat_messages;
CREATE TRIGGER chat_messages_cleanup_notifications_after_delete
    AFTER DELETE ON public.chat_messages
    FOR EACH ROW EXECUTE FUNCTION public.cleanup_chat_notifications_for_deleted_message();
