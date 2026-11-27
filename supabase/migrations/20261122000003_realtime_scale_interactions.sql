-- =============================================================================
-- MEU PAÍS — Migração 003: Rede Social 100% Online, Escalável e em Tempo Real
-- Comentários aninhados + curtidas, views, partilhas, rate limiting anti-spam,
-- gatilhos automáticos de notificações, Storage CDN e Realtime publication.
-- =============================================================================

-- 1. Evolução da tabela POSTS (edição, visualizações, categoria)
ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS category TEXT,
    ADD COLUMN IF NOT EXISTS views_count INTEGER NOT NULL DEFAULT 0 CHECK (views_count >= 0),
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_posts_feed_cursor ON public.posts (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_posts_location_created ON public.posts (location_label, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_category_created ON public.posts (category, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_visibility_created ON public.posts (visibility, created_at DESC);

-- 2. Visualizações únicas por utilizador em POSTS
CREATE TABLE IF NOT EXISTS public.post_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    viewer_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, viewer_id)
);

ALTER TABLE public.post_views ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "post_views_own" ON public.post_views;
CREATE POLICY "post_views_own" ON public.post_views
    FOR ALL USING (viewer_id = public.firebase_uid()) WITH CHECK (viewer_id = public.firebase_uid());

-- 3. Comentários em POSTS e VÍDEOS + respostas (parent_id) + curtidas em comentários
ALTER TABLE public.comments
    ALTER COLUMN post_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS video_id UUID REFERENCES public.videos(id) ON DELETE CASCADE;

ALTER TABLE public.comments DROP CONSTRAINT IF EXISTS comments_target_check;
ALTER TABLE public.comments
    ADD CONSTRAINT comments_target_check CHECK (
        ((post_id IS NOT NULL)::int + (video_id IS NOT NULL)::int) = 1
    );

CREATE INDEX IF NOT EXISTS idx_comments_video_created ON public.comments (video_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_comments_parent ON public.comments (parent_id, created_at ASC);

CREATE TABLE IF NOT EXISTS public.comment_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    comment_id UUID NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, comment_id)
);

ALTER TABLE public.comment_likes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "comment_likes_read" ON public.comment_likes;
DROP POLICY IF EXISTS "comment_likes_own" ON public.comment_likes;
CREATE POLICY "comment_likes_read" ON public.comment_likes
    FOR SELECT USING (public.firebase_uid() IS NOT NULL);
CREATE POLICY "comment_likes_own" ON public.comment_likes
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());

-- 4. Curtidas únicas por utilizador em VÍDEOS (Reels)
CREATE TABLE IF NOT EXISTS public.video_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    video_id UUID NOT NULL REFERENCES public.videos(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, video_id)
);

ALTER TABLE public.video_likes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "video_likes_read" ON public.video_likes;
DROP POLICY IF EXISTS "video_likes_own" ON public.video_likes;
CREATE POLICY "video_likes_read" ON public.video_likes
    FOR SELECT USING (public.firebase_uid() IS NOT NULL);
CREATE POLICY "video_likes_own" ON public.video_likes
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());

CREATE INDEX IF NOT EXISTS idx_videos_created ON public.videos (created_at DESC, id DESC);

-- 5. Expandir tipos de notificação (incluindo comment_reply) e leitura/entrega de mensagens
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_event_type_check;
ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_event_type_check
    CHECK (event_type IN ('follow', 'like', 'comment', 'comment_reply', 'message', 'mention', 'invite', 'live_stream', 'system'));

ALTER TABLE public.messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE public.messages
    ADD CONSTRAINT messages_message_type_check
    CHECK (message_type IN ('text', 'image', 'video', 'audio', 'document', 'call_event'));

-- Políticas adicionais para grupos, comunidades, media, video_views, mensagens e bloqueios
DROP POLICY IF EXISTS "media_read_all" ON public.media;
DROP POLICY IF EXISTS "media_manage_own" ON public.media;
CREATE POLICY "media_read_all" ON public.media
    FOR SELECT USING (public.firebase_uid() IS NOT NULL);
CREATE POLICY "media_manage_own" ON public.media
    FOR ALL USING (owner_id = public.firebase_uid()) WITH CHECK (owner_id = public.firebase_uid());

DROP POLICY IF EXISTS "video_views_own" ON public.video_views;
CREATE POLICY "video_views_own" ON public.video_views
    FOR ALL USING (viewer_id = public.firebase_uid()) WITH CHECK (viewer_id = public.firebase_uid());

DROP POLICY IF EXISTS "groups_read_all" ON public.groups;
DROP POLICY IF EXISTS "groups_insert_own" ON public.groups;
CREATE POLICY "groups_read_all" ON public.groups
    FOR SELECT USING (public.firebase_uid() IS NOT NULL AND (is_private = FALSE OR owner_id = public.firebase_uid()));
CREATE POLICY "groups_insert_own" ON public.groups
    FOR INSERT WITH CHECK (owner_id = public.firebase_uid());

DROP POLICY IF EXISTS "group_members_read" ON public.group_members;
DROP POLICY IF EXISTS "group_members_own" ON public.group_members;
CREATE POLICY "group_members_read" ON public.group_members
    FOR SELECT USING (public.firebase_uid() IS NOT NULL);
CREATE POLICY "group_members_own" ON public.group_members
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());

-- Respeitar bloqueios em comentários e mensagens
DROP POLICY IF EXISTS "comments_read" ON public.comments;
CREATE POLICY "comments_read" ON public.comments
    FOR SELECT USING (
        public.firebase_uid() IS NOT NULL
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE (b.blocker_id = public.firebase_uid() AND b.blocked_id = author_id)
               OR (b.blocker_id = author_id AND b.blocked_id = public.firebase_uid())
        )
    );

DROP POLICY IF EXISTS "messages_member_update" ON public.messages;
CREATE POLICY "messages_member_update" ON public.messages
    FOR UPDATE USING (
        EXISTS (
            SELECT 1 FROM public.conversation_members cm
            WHERE cm.conversation_id = conversation_id AND cm.user_id = public.firebase_uid()
        )
    );

-- Política de update seguro no perfil: impedir auto-promoção de role/is_verified/is_suspended pelo cliente
DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (id = public.firebase_uid())
    WITH CHECK (
        id = public.firebase_uid()
        AND role = (SELECT p.role FROM public.profiles p WHERE p.id = public.firebase_uid())
        AND is_verified = (SELECT p.is_verified FROM public.profiles p WHERE p.id = public.firebase_uid())
        AND is_suspended = (SELECT p.is_suspended FROM public.profiles p WHERE p.id = public.firebase_uid())
    );

-- 6. Proteção Anti-Spam / Bots & Rate Limiting por Utilizador no Servidor
CREATE TABLE IF NOT EXISTS public.rate_limits (
    user_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    action_key TEXT NOT NULL,
    window_start TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    hits INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, action_key)
);

ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.enforce_rate_limit(
    p_user_id TEXT,
    p_action_key TEXT,
    p_max_hits INTEGER,
    p_window_seconds INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_window TIMESTAMPTZ;
    v_hits INTEGER;
BEGIN
    IF p_user_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO public.rate_limits (user_id, action_key, window_start, hits)
    VALUES (p_user_id, p_action_key, NOW(), 1)
    ON CONFLICT (user_id, action_key) DO UPDATE SET
        hits = CASE
            WHEN public.rate_limits.window_start + make_interval(secs => p_window_seconds) < NOW() THEN 1
            ELSE public.rate_limits.hits + 1
        END,
        window_start = CASE
            WHEN public.rate_limits.window_start + make_interval(secs => p_window_seconds) < NOW() THEN NOW()
            ELSE public.rate_limits.window_start
        END
    RETURNING window_start, hits INTO v_window, v_hits;

    IF v_hits > p_max_hits THEN
        RAISE EXCEPTION 'Limite de requisições excedido para %. Aguarde alguns instantes.', p_action_key
            USING ERRCODE = 'P0001';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.trg_rate_limit_check()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_TABLE_NAME = 'posts' THEN
        PERFORM public.enforce_rate_limit(NEW.author_id, 'create_post', 12, 60);
    ELSIF TG_TABLE_NAME = 'comments' THEN
        PERFORM public.enforce_rate_limit(NEW.author_id, 'create_comment', 30, 60);
    ELSIF TG_TABLE_NAME = 'messages' THEN
        PERFORM public.enforce_rate_limit(NEW.sender_id, 'send_message', 60, 60);
    ELSIF TG_TABLE_NAME = 'follows' THEN
        PERFORM public.enforce_rate_limit(NEW.follower_id, 'follow_user', 40, 60);
    ELSIF TG_TABLE_NAME = 'reports' THEN
        PERFORM public.enforce_rate_limit(NEW.reporter_id, 'submit_report', 10, 60);
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS rl_posts_insert ON public.posts;
CREATE TRIGGER rl_posts_insert BEFORE INSERT ON public.posts FOR EACH ROW EXECUTE FUNCTION public.trg_rate_limit_check();

DROP TRIGGER IF EXISTS rl_comments_insert ON public.comments;
CREATE TRIGGER rl_comments_insert BEFORE INSERT ON public.comments FOR EACH ROW EXECUTE FUNCTION public.trg_rate_limit_check();

DROP TRIGGER IF EXISTS rl_messages_insert ON public.messages;
CREATE TRIGGER rl_messages_insert BEFORE INSERT ON public.messages FOR EACH ROW EXECUTE FUNCTION public.trg_rate_limit_check();

DROP TRIGGER IF EXISTS rl_follows_insert ON public.follows;
CREATE TRIGGER rl_follows_insert BEFORE INSERT ON public.follows FOR EACH ROW EXECUTE FUNCTION public.trg_rate_limit_check();

DROP TRIGGER IF EXISTS rl_reports_insert ON public.reports;
CREATE TRIGGER rl_reports_insert BEFORE INSERT ON public.reports FOR EACH ROW EXECUTE FUNCTION public.trg_rate_limit_check();

-- 7. Gatilhos Atómicos para Contadores Reais + Notificações Reais
CREATE OR REPLACE FUNCTION public.trg_sync_post_counts()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.profiles SET posts_count = posts_count + 1 WHERE id = NEW.author_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.profiles SET posts_count = GREATEST(0, posts_count - 1) WHERE id = OLD.author_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_post_count_change ON public.posts;
CREATE TRIGGER on_post_count_change AFTER INSERT OR DELETE ON public.posts FOR EACH ROW EXECUTE FUNCTION public.trg_sync_post_counts();

CREATE OR REPLACE FUNCTION public.trg_sync_like_counts_and_notify()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_author_id TEXT;
    v_actor_name TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.posts SET likes_count = likes_count + 1 WHERE id = NEW.post_id RETURNING author_id INTO v_author_id;
        IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
            SELECT full_name INTO v_actor_name FROM public.profiles WHERE id = NEW.user_id;
            INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
            VALUES (v_author_id, NEW.user_id, 'like', coalesce(v_actor_name, 'Alguém') || ' curtiu a tua publicação', 'Toca para ver a tua publicação.', NEW.post_id);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.posts SET likes_count = GREATEST(0, likes_count - 1) WHERE id = OLD.post_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_post_like_change ON public.likes;
CREATE TRIGGER on_post_like_change AFTER INSERT OR DELETE ON public.likes FOR EACH ROW EXECUTE FUNCTION public.trg_sync_like_counts_and_notify();

CREATE OR REPLACE FUNCTION public.trg_sync_video_like_counts()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_author_id TEXT;
    v_actor_name TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.videos SET likes_count = likes_count + 1 WHERE id = NEW.video_id RETURNING author_id INTO v_author_id;
        IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
            SELECT full_name INTO v_actor_name FROM public.profiles WHERE id = NEW.user_id;
            INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
            VALUES (v_author_id, NEW.user_id, 'like', coalesce(v_actor_name, 'Alguém') || ' curtiu o teu vídeo', 'O teu vídeo recebeu uma nova curtida.', NEW.video_id);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.videos SET likes_count = GREATEST(0, likes_count - 1) WHERE id = OLD.video_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_video_like_change ON public.video_likes;
CREATE TRIGGER on_video_like_change AFTER INSERT OR DELETE ON public.video_likes FOR EACH ROW EXECUTE FUNCTION public.trg_sync_video_like_counts();

CREATE OR REPLACE FUNCTION public.trg_sync_comment_like_counts()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.comments SET likes_count = likes_count + 1 WHERE id = NEW.comment_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.comments SET likes_count = GREATEST(0, likes_count - 1) WHERE id = OLD.comment_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_comment_like_change ON public.comment_likes;
CREATE TRIGGER on_comment_like_change AFTER INSERT OR DELETE ON public.comment_likes FOR EACH ROW EXECUTE FUNCTION public.trg_sync_comment_like_counts();

CREATE OR REPLACE FUNCTION public.trg_sync_comments_and_notify()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_target_author TEXT;
    v_parent_author TEXT;
    v_actor_name TEXT;
    v_mention_match TEXT[];
    v_mentioned_uid TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT full_name INTO v_actor_name FROM public.profiles WHERE id = NEW.author_id;

        IF NEW.post_id IS NOT NULL THEN
            UPDATE public.posts SET comments_count = comments_count + 1 WHERE id = NEW.post_id RETURNING author_id INTO v_target_author;
            IF v_target_author IS NOT NULL AND v_target_author <> NEW.author_id THEN
                INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
                VALUES (v_target_author, NEW.author_id, 'comment', coalesce(v_actor_name, 'Alguém') || ' comentou na tua publicação', left(NEW.body, 140), NEW.post_id);
            END IF;
        ELSIF NEW.video_id IS NOT NULL THEN
            UPDATE public.videos SET comments_count = comments_count + 1 WHERE id = NEW.video_id RETURNING author_id INTO v_target_author;
            IF v_target_author IS NOT NULL AND v_target_author <> NEW.author_id THEN
                INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
                VALUES (v_target_author, NEW.author_id, 'comment', coalesce(v_actor_name, 'Alguém') || ' comentou no teu vídeo', left(NEW.body, 140), NEW.video_id);
            END IF;
        END IF;

        IF NEW.parent_id IS NOT NULL THEN
            SELECT author_id INTO v_parent_author FROM public.comments WHERE id = NEW.parent_id;
            IF v_parent_author IS NOT NULL AND v_parent_author <> NEW.author_id AND v_parent_author IS DISTINCT FROM v_target_author THEN
                INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
                VALUES (v_parent_author, NEW.author_id, 'comment_reply', coalesce(v_actor_name, 'Alguém') || ' respondeu ao teu comentário', left(NEW.body, 140), coalesce(NEW.post_id, NEW.video_id));
            END IF;
        END IF;

        -- Detetar menções @username reais
        FOR v_mention_match IN SELECT regexp_matches(NEW.body, '@([a-zA-Z0-9._]{3,32})', 'g') LOOP
            SELECT id INTO v_mentioned_uid FROM public.profiles WHERE lower(username) = lower(v_mention_match[1]) LIMIT 1;
            IF v_mentioned_uid IS NOT NULL AND v_mentioned_uid <> NEW.author_id THEN
                INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
                VALUES (v_mentioned_uid, NEW.author_id, 'mention', coalesce(v_actor_name, 'Alguém') || ' mencionou-te', left(NEW.body, 140), coalesce(NEW.post_id, NEW.video_id));
            END IF;
        END LOOP;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.post_id IS NOT NULL THEN
            UPDATE public.posts SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.post_id;
        ELSIF OLD.video_id IS NOT NULL THEN
            UPDATE public.videos SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.video_id;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_comment_change ON public.comments;
CREATE TRIGGER on_comment_change AFTER INSERT OR DELETE ON public.comments FOR EACH ROW EXECUTE FUNCTION public.trg_sync_comments_and_notify();

CREATE OR REPLACE FUNCTION public.trg_sync_follows_and_notify()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_actor_name TEXT;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'accepted' THEN
        UPDATE public.profiles SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE public.profiles SET followers_count = followers_count + 1 WHERE id = NEW.following_id;
        SELECT full_name INTO v_actor_name FROM public.profiles WHERE id = NEW.follower_id;
        INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body)
        VALUES (NEW.following_id, NEW.follower_id, 'follow', coalesce(v_actor_name, 'Novo utilizador') || ' começou a seguir-te', 'Toca para visitar o perfil.');
    ELSIF TG_OP = 'DELETE' AND OLD.status = 'accepted' THEN
        UPDATE public.profiles SET following_count = GREATEST(0, following_count - 1) WHERE id = OLD.follower_id;
        UPDATE public.profiles SET followers_count = GREATEST(0, followers_count - 1) WHERE id = OLD.following_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_follow_change ON public.follows;
CREATE TRIGGER on_follow_change AFTER INSERT OR DELETE ON public.follows FOR EACH ROW EXECUTE FUNCTION public.trg_sync_follows_and_notify();

CREATE OR REPLACE FUNCTION public.trg_sync_views()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_TABLE_NAME = 'post_views' THEN
        UPDATE public.posts SET views_count = views_count + 1 WHERE id = NEW.post_id;
    ELSIF TG_TABLE_NAME = 'video_views' THEN
        UPDATE public.videos SET views_count = views_count + 1 WHERE id = NEW.video_id;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_post_view_insert ON public.post_views;
CREATE TRIGGER on_post_view_insert AFTER INSERT ON public.post_views FOR EACH ROW EXECUTE FUNCTION public.trg_sync_views();

DROP TRIGGER IF EXISTS on_video_view_insert ON public.video_views;
CREATE TRIGGER on_video_view_insert AFTER INSERT ON public.video_views FOR EACH ROW EXECUTE FUNCTION public.trg_sync_views();

CREATE OR REPLACE FUNCTION public.trg_sync_message_and_notify()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_sender_name TEXT;
    v_member RECORD;
BEGIN
    UPDATE public.conversations
    SET last_message_preview = CASE WHEN NEW.body <> '' THEN left(NEW.body, 80) ELSE NEW.message_type END,
        last_message_at = NEW.created_at
    WHERE id = NEW.conversation_id;

    SELECT full_name INTO v_sender_name FROM public.profiles WHERE id = NEW.sender_id;

    FOR v_member IN
        SELECT cm.user_id FROM public.conversation_members cm
        WHERE cm.conversation_id = NEW.conversation_id
          AND cm.user_id <> NEW.sender_id
          AND NOT EXISTS (
              SELECT 1 FROM public.blocks b
              WHERE (b.blocker_id = cm.user_id AND b.blocked_id = NEW.sender_id)
                 OR (b.blocker_id = NEW.sender_id AND b.blocked_id = cm.user_id)
          )
    LOOP
        INSERT INTO public.notifications (recipient_id, actor_id, event_type, title, body, reference_id)
        VALUES (v_member.user_id, NEW.sender_id, 'message', coalesce(v_sender_name, 'Nova mensagem'), left(coalesce(nullif(NEW.body, ''), 'Anexo enviado'), 120), NEW.conversation_id);
    END LOOP;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS on_message_insert ON public.messages;
CREATE TRIGGER on_message_insert AFTER INSERT ON public.messages FOR EACH ROW EXECUTE FUNCTION public.trg_sync_message_and_notify();

-- 8. RPCs Seguros para Conversas Reais, Leitura, Partilhas e Monitorização de Erros
CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(p_target_user_id TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_conv_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Autenticação necessária';
    END IF;
    IF p_target_user_id IS NULL OR p_target_user_id = v_uid THEN
        RAISE EXCEPTION 'Destinatário inválido';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.blocks b
        WHERE (b.blocker_id = v_uid AND b.blocked_id = p_target_user_id)
           OR (b.blocker_id = p_target_user_id AND b.blocked_id = v_uid)
    ) THEN
        RAISE EXCEPTION 'Não é possível iniciar conversa devido a bloqueio ativo';
    END IF;

    SELECT c.id INTO v_conv_id
    FROM public.conversations c
    JOIN public.conversation_members m1 ON m1.conversation_id = c.id AND m1.user_id = v_uid
    JOIN public.conversation_members m2 ON m2.conversation_id = c.id AND m2.user_id = p_target_user_id
    WHERE c.is_group = FALSE
    LIMIT 1;

    IF v_conv_id IS NOT NULL THEN
        RETURN v_conv_id;
    END IF;

    INSERT INTO public.conversations (is_group, last_message_preview)
    VALUES (FALSE, '')
    RETURNING id INTO v_conv_id;

    INSERT INTO public.conversation_members (conversation_id, user_id, role)
    VALUES (v_conv_id, v_uid, 'member'), (v_conv_id, p_target_user_id, 'member')
    ON CONFLICT DO NOTHING;

    RETURN v_conv_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_conversation_read(p_conversation_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
BEGIN
    IF v_uid IS NULL THEN
        RETURN;
    END IF;
    UPDATE public.conversation_members
    SET last_read_at = NOW()
    WHERE conversation_id = p_conversation_id AND user_id = v_uid;

    UPDATE public.messages
    SET delivery_status = 'read'
    WHERE conversation_id = p_conversation_id
      AND sender_id <> v_uid
      AND delivery_status <> 'read';
END;
$$;

CREATE OR REPLACE FUNCTION public.increment_post_share(p_post_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF public.firebase_uid() IS NULL THEN
        RAISE EXCEPTION 'Autenticação necessária';
    END IF;
    UPDATE public.posts SET shares_count = shares_count + 1 WHERE id = p_post_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.increment_video_share(p_video_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF public.firebase_uid() IS NULL THEN
        RAISE EXCEPTION 'Autenticação necessária';
    END IF;
    UPDATE public.videos SET shares_count = shares_count + 1 WHERE id = p_video_id;
END;
$$;

-- Tabela de telemetria / monitorização de erros
CREATE TABLE IF NOT EXISTS public.error_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT REFERENCES public.profiles(id) ON DELETE SET NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    screen TEXT,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.error_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "error_logs_insert_auth" ON public.error_logs;
DROP POLICY IF EXISTS "error_logs_admin_read" ON public.error_logs;
CREATE POLICY "error_logs_insert_auth" ON public.error_logs
    FOR INSERT WITH CHECK (public.firebase_uid() IS NOT NULL);
CREATE POLICY "error_logs_admin_read" ON public.error_logs
    FOR SELECT USING (public.is_admin_or_mod());

-- 9. Storage Bucket público (CDN) para mídias reais com RLS por UID Firebase
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'media',
    'media',
    TRUE,
    52428800,
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/quicktime', 'audio/mp4', 'audio/aac', 'audio/mpeg', 'application/pdf']
)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "media_bucket_public_read" ON storage.objects;
DROP POLICY IF EXISTS "media_bucket_auth_upload" ON storage.objects;
DROP POLICY IF EXISTS "media_bucket_owner_delete" ON storage.objects;

CREATE POLICY "media_bucket_public_read" ON storage.objects
    FOR SELECT USING (bucket_id = 'media');

CREATE POLICY "media_bucket_auth_upload" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'media'
        AND public.firebase_uid() IS NOT NULL
        AND (storage.foldername(name))[1] = public.firebase_uid()
    );

CREATE POLICY "media_bucket_owner_delete" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'media'
        AND public.firebase_uid() IS NOT NULL
        AND (storage.foldername(name))[1] = public.firebase_uid()
    );

-- 10. Ativar Supabase Realtime nas tabelas sociais principais
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['posts', 'comments', 'likes', 'stories', 'messages', 'conversations', 'notifications', 'follows', 'videos'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = tbl
        ) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
        END IF;
    END LOOP;
END $$;
