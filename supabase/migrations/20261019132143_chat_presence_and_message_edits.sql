-- Track account activity for presence and allow safe editing/deleting of authored messages.
ALTER TABLE public.chat_profiles
    ADD COLUMN IF NOT EXISTS last_active_at timestamptz;

UPDATE public.chat_profiles
   SET last_active_at = created_at
 WHERE last_active_at IS NULL;

ALTER TABLE public.chat_profiles
    ALTER COLUMN last_active_at SET DEFAULT now(),
    ALTER COLUMN last_active_at SET NOT NULL,
    REPLICA IDENTITY FULL;

ALTER TABLE public.chat_messages
    ADD COLUMN IF NOT EXISTS edited_at timestamptz;
ALTER TABLE public.chat_messages REPLICA IDENTITY FULL;

CREATE OR REPLACE FUNCTION public.touch_chat_presence()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    current_user_id uuid := auth.uid();
BEGIN
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'Sign in to update chat presence.' USING errcode = '28000';
    END IF;

    UPDATE public.chat_profiles
       SET last_active_at = now()
     WHERE id = current_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.touch_chat_presence() FROM public, anon;
GRANT EXECUTE ON FUNCTION public.touch_chat_presence() TO authenticated;

CREATE OR REPLACE FUNCTION public.set_chat_message_edited_at()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.body IS DISTINCT FROM OLD.body THEN
        NEW.edited_at := now();
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_messages_set_edited_at ON public.chat_messages;
CREATE TRIGGER chat_messages_set_edited_at
    BEFORE UPDATE OF body ON public.chat_messages
    FOR EACH ROW EXECUTE FUNCTION public.set_chat_message_edited_at();

CREATE OR REPLACE FUNCTION public.update_chat_notification_preview_for_edit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.body IS DISTINCT FROM OLD.body THEN
        UPDATE public.chat_notifications
           SET message_preview = left(NEW.body, 180)
         WHERE message_id = NEW.id
           AND read_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS chat_messages_update_notification_preview ON public.chat_messages;
CREATE TRIGGER chat_messages_update_notification_preview
    AFTER UPDATE OF body ON public.chat_messages
    FOR EACH ROW EXECUTE FUNCTION public.update_chat_notification_preview_for_edit();

DROP POLICY IF EXISTS chat_messages_sender_update ON public.chat_messages;
CREATE POLICY chat_messages_sender_update
    ON public.chat_messages FOR UPDATE TO authenticated
    USING (
        sender_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.chat_conversations c
             WHERE c.id = chat_messages.conversation_id
               AND (auth.uid() = c.user_a OR auth.uid() = c.user_b)
        )
    )
    WITH CHECK (
        sender_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.chat_conversations c
             WHERE c.id = chat_messages.conversation_id
               AND (auth.uid() = c.user_a OR auth.uid() = c.user_b)
        )
    );

DROP POLICY IF EXISTS chat_messages_sender_delete ON public.chat_messages;
CREATE POLICY chat_messages_sender_delete
    ON public.chat_messages FOR DELETE TO authenticated
    USING (
        sender_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.chat_conversations c
             WHERE c.id = chat_messages.conversation_id
               AND (auth.uid() = c.user_a OR auth.uid() = c.user_b)
        )
    );

REVOKE UPDATE, DELETE ON TABLE public.chat_messages FROM authenticated;
GRANT UPDATE (body) ON TABLE public.chat_messages TO authenticated;
GRANT DELETE ON TABLE public.chat_messages TO authenticated;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
       AND NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
             WHERE pubname = 'supabase_realtime'
               AND schemaname = 'public'
               AND tablename = 'chat_profiles'
       ) THEN
        EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_profiles';
    END IF;
END $$;
