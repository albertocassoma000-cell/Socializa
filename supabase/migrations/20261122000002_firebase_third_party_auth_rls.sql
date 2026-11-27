-- =============================================================================
-- MEU PAÍS — Migração 002: Autenticação Principal Firebase + RLS Supabase
-- Validação de tokens Firebase (Third-Party Auth) sem senhas ou auth.users no Supabase
-- =============================================================================

-- 1. Função segura que extrai o UID verificado do JWT do Firebase validado pelo Supabase.
-- Nunca aceita user_id arbitrário enviado no corpo do pedido.
CREATE OR REPLACE FUNCTION public.firebase_uid()
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        WHEN coalesce(current_setting('request.jwt.claim.role', true), auth.role()) = 'authenticated'
        THEN nullif(auth.jwt()->>'sub', '')
        ELSE NULL
    END;
$$;

CREATE OR REPLACE FUNCTION public.firebase_email()
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
    SELECT nullif(auth.jwt()->>'email', '');
$$;

-- 2. Remover trigger e dependência de auth.users (sem duplicação de identidade nem senhas no Supabase)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();

-- 3. Garantir que profiles.id e colunas de autoria usam TEXT (Firebase UID sincronizado)
-- e remover FK para auth.users caso exista de migração anterior.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu
          ON tc.constraint_name = ccu.constraint_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_schema = 'public'
          AND ccu.table_schema = 'auth'
          AND ccu.table_name = 'users'
    ) LOOP
        EXECUTE format('ALTER TABLE public.%I DROP CONSTRAINT IF EXISTS %I', r.table_name, r.constraint_name);
    END LOOP;
END $$;

-- Converter colunas de ID do utilizador para TEXT (compatível com Firebase UIDs alfanuméricos)
ALTER TABLE public.profiles
    ALTER COLUMN id TYPE TEXT USING id::text;

ALTER TABLE public.posts
    ALTER COLUMN author_id TYPE TEXT USING author_id::text;

ALTER TABLE public.media
    ALTER COLUMN owner_id TYPE TEXT USING owner_id::text;

ALTER TABLE public.likes
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.saved_posts
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.comments
    ALTER COLUMN author_id TYPE TEXT USING author_id::text;

ALTER TABLE public.follows
    ALTER COLUMN follower_id TYPE TEXT USING follower_id::text,
    ALTER COLUMN following_id TYPE TEXT USING following_id::text;

ALTER TABLE public.stories
    ALTER COLUMN author_id TYPE TEXT USING author_id::text;

ALTER TABLE public.story_views
    ALTER COLUMN viewer_id TYPE TEXT USING viewer_id::text;

ALTER TABLE public.videos
    ALTER COLUMN author_id TYPE TEXT USING author_id::text;

ALTER TABLE public.video_views
    ALTER COLUMN viewer_id TYPE TEXT USING viewer_id::text;

ALTER TABLE public.live_streams
    ALTER COLUMN host_id TYPE TEXT USING host_id::text;

ALTER TABLE public.groups
    ALTER COLUMN owner_id TYPE TEXT USING owner_id::text;

ALTER TABLE public.group_members
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.conversation_members
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.messages
    ALTER COLUMN sender_id TYPE TEXT USING sender_id::text;

ALTER TABLE public.device_push_tokens
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.notifications
    ALTER COLUMN recipient_id TYPE TEXT USING recipient_id::text,
    ALTER COLUMN actor_id TYPE TEXT USING actor_id::text;

ALTER TABLE public.products
    ALTER COLUMN seller_id TYPE TEXT USING seller_id::text;

ALTER TABLE public.advertisements
    ALTER COLUMN advertiser_id TYPE TEXT USING advertiser_id::text;

ALTER TABLE public.subscriptions
    ALTER COLUMN subscriber_id TYPE TEXT USING subscriber_id::text,
    ALTER COLUMN creator_id TYPE TEXT USING creator_id::text;

ALTER TABLE public.transactions
    ALTER COLUMN user_id TYPE TEXT USING user_id::text;

ALTER TABLE public.blocks
    ALTER COLUMN blocker_id TYPE TEXT USING blocker_id::text,
    ALTER COLUMN blocked_id TYPE TEXT USING blocked_id::text;

ALTER TABLE public.reports
    ALTER COLUMN reporter_id TYPE TEXT USING reporter_id::text,
    ALTER COLUMN reviewed_by TYPE TEXT USING reviewed_by::text;

ALTER TABLE public.audit_logs
    ALTER COLUMN actor_id TYPE TEXT USING actor_id::text;

-- 4. Atualizar is_admin_or_mod() para usar o UID verificado do Firebase JWT
CREATE OR REPLACE FUNCTION public.is_admin_or_mod()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = public.firebase_uid()
          AND role IN ('admin', 'moderator')
          AND is_suspended = FALSE
    );
$$;

-- 5. Recriar políticas RLS estritas baseadas em public.firebase_uid()
DROP POLICY IF EXISTS "profiles_read_all" ON public.profiles;
DROP POLICY IF EXISTS "profiles_insert_own" ON public.profiles;
DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;

CREATE POLICY "profiles_read_all" ON public.profiles
    FOR SELECT USING (is_suspended = FALSE OR id = public.firebase_uid() OR public.is_admin_or_mod());
CREATE POLICY "profiles_insert_own" ON public.profiles
    FOR INSERT WITH CHECK (id = public.firebase_uid());
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (id = public.firebase_uid()) WITH CHECK (id = public.firebase_uid());

DROP POLICY IF EXISTS "posts_read_visible" ON public.posts;
DROP POLICY IF EXISTS "posts_insert_own" ON public.posts;
DROP POLICY IF EXISTS "posts_update_own" ON public.posts;
DROP POLICY IF EXISTS "posts_delete_own" ON public.posts;

CREATE POLICY "posts_read_visible" ON public.posts
    FOR SELECT USING (
        public.firebase_uid() IS NOT NULL
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE (b.blocker_id = public.firebase_uid() AND b.blocked_id = author_id)
               OR (b.blocker_id = author_id AND b.blocked_id = public.firebase_uid())
        )
        AND (
            visibility = 'public'
            OR author_id = public.firebase_uid()
            OR (visibility = 'followers' AND EXISTS (
                SELECT 1 FROM public.follows f
                WHERE f.follower_id = public.firebase_uid() AND f.following_id = author_id AND f.status = 'accepted'
            ))
            OR public.is_admin_or_mod()
        )
    );
CREATE POLICY "posts_insert_own" ON public.posts
    FOR INSERT WITH CHECK (author_id = public.firebase_uid());
CREATE POLICY "posts_update_own" ON public.posts
    FOR UPDATE USING (author_id = public.firebase_uid() OR public.is_admin_or_mod());
CREATE POLICY "posts_delete_own" ON public.posts
    FOR DELETE USING (author_id = public.firebase_uid() OR public.is_admin_or_mod());

DROP POLICY IF EXISTS "likes_write_own" ON public.likes;
CREATE POLICY "likes_write_own" ON public.likes
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());

DROP POLICY IF EXISTS "saved_posts_own" ON public.saved_posts;
CREATE POLICY "saved_posts_own" ON public.saved_posts
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());

DROP POLICY IF EXISTS "comments_insert_own" ON public.comments;
DROP POLICY IF EXISTS "comments_delete_own" ON public.comments;
CREATE POLICY "comments_insert_own" ON public.comments
    FOR INSERT WITH CHECK (author_id = public.firebase_uid());
CREATE POLICY "comments_delete_own" ON public.comments
    FOR DELETE USING (author_id = public.firebase_uid() OR public.is_admin_or_mod());

DROP POLICY IF EXISTS "follows_manage_own" ON public.follows;
CREATE POLICY "follows_manage_own" ON public.follows
    FOR ALL USING (follower_id = public.firebase_uid()) WITH CHECK (follower_id = public.firebase_uid());

DROP POLICY IF EXISTS "stories_read_active" ON public.stories;
DROP POLICY IF EXISTS "stories_insert_own" ON public.stories;
DROP POLICY IF EXISTS "stories_delete_own" ON public.stories;
CREATE POLICY "stories_read_active" ON public.stories
    FOR SELECT USING (public.firebase_uid() IS NOT NULL AND (expires_at > NOW() OR author_id = public.firebase_uid()));
CREATE POLICY "stories_insert_own" ON public.stories
    FOR INSERT WITH CHECK (author_id = public.firebase_uid());
CREATE POLICY "stories_delete_own" ON public.stories
    FOR DELETE USING (author_id = public.firebase_uid());

DROP POLICY IF EXISTS "story_views_own" ON public.story_views;
CREATE POLICY "story_views_own" ON public.story_views
    FOR ALL USING (viewer_id = public.firebase_uid()) WITH CHECK (viewer_id = public.firebase_uid());

DROP POLICY IF EXISTS "conversations_member_read" ON public.conversations;
DROP POLICY IF EXISTS "conversation_members_read" ON public.conversation_members;
DROP POLICY IF EXISTS "messages_member_read" ON public.messages;
DROP POLICY IF EXISTS "messages_member_insert" ON public.messages;

CREATE POLICY "conversations_member_read" ON public.conversations
    FOR SELECT USING (EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = id AND cm.user_id = public.firebase_uid()
    ));
CREATE POLICY "conversation_members_read" ON public.conversation_members
    FOR SELECT USING (user_id = public.firebase_uid() OR EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = public.firebase_uid()
    ));
CREATE POLICY "messages_member_read" ON public.messages
    FOR SELECT USING (EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = public.firebase_uid()
    ));
CREATE POLICY "messages_member_insert" ON public.messages
    FOR INSERT WITH CHECK (
        sender_id = public.firebase_uid()
        AND EXISTS (
            SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = public.firebase_uid()
        )
    );

DROP POLICY IF EXISTS "push_tokens_own" ON public.device_push_tokens;
DROP POLICY IF EXISTS "notifications_own" ON public.notifications;
DROP POLICY IF EXISTS "notifications_update_own" ON public.notifications;
CREATE POLICY "push_tokens_own" ON public.device_push_tokens
    FOR ALL USING (user_id = public.firebase_uid()) WITH CHECK (user_id = public.firebase_uid());
CREATE POLICY "notifications_own" ON public.notifications
    FOR SELECT USING (recipient_id = public.firebase_uid());
CREATE POLICY "notifications_update_own" ON public.notifications
    FOR UPDATE USING (recipient_id = public.firebase_uid()) WITH CHECK (recipient_id = public.firebase_uid());

DROP POLICY IF EXISTS "videos_insert_own" ON public.videos;
CREATE POLICY "videos_insert_own" ON public.videos
    FOR INSERT WITH CHECK (author_id = public.firebase_uid());

DROP POLICY IF EXISTS "products_read_active" ON public.products;
DROP POLICY IF EXISTS "products_manage_own" ON public.products;
CREATE POLICY "products_read_active" ON public.products
    FOR SELECT USING (public.firebase_uid() IS NOT NULL AND (status = 'active' OR seller_id = public.firebase_uid() OR public.is_admin_or_mod()));
CREATE POLICY "products_manage_own" ON public.products
    FOR ALL USING (seller_id = public.firebase_uid()) WITH CHECK (seller_id = public.firebase_uid());

DROP POLICY IF EXISTS "blocks_own" ON public.blocks;
DROP POLICY IF EXISTS "reports_insert_auth" ON public.reports;
DROP POLICY IF EXISTS "reports_admin_read" ON public.reports;
CREATE POLICY "blocks_own" ON public.blocks
    FOR ALL USING (blocker_id = public.firebase_uid()) WITH CHECK (blocker_id = public.firebase_uid());
CREATE POLICY "reports_insert_auth" ON public.reports
    FOR INSERT WITH CHECK (reporter_id = public.firebase_uid());
CREATE POLICY "reports_admin_read" ON public.reports
    FOR SELECT USING (reporter_id = public.firebase_uid() OR public.is_admin_or_mod());

-- 6. RPC seguro para cadastro/sincronização automática do perfil no Supabase
-- O UID é sempre extraído do JWT verificado no servidor (public.firebase_uid()), nunca do cliente.
CREATE OR REPLACE FUNCTION public.ensure_firebase_profile(
    p_full_name TEXT DEFAULT NULL,
    p_avatar_url TEXT DEFAULT NULL,
    p_province TEXT DEFAULT 'Luanda',
    p_country TEXT DEFAULT 'Angola'
)
RETURNS public.profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_email TEXT := coalesce(public.firebase_email(), '');
    v_base_username TEXT;
    v_final_username TEXT;
    v_profile public.profiles;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Token Firebase inválido ou ausente';
    END IF;

    SELECT * INTO v_profile FROM public.profiles WHERE id = v_uid;
    IF FOUND THEN
        RETURN v_profile;
    END IF;

    v_base_username := regexp_replace(
        lower(coalesce(nullif(split_part(v_email, '@', 1), ''), 'cidadao')),
        '[^a-z0-9._]', '', 'g'
    );
    IF char_length(v_base_username) < 3 THEN
        v_base_username := 'cidadao';
    END IF;
    v_final_username := left(v_base_username, 22) || '_' || lower(substr(regexp_replace(v_uid, '[^a-zA-Z0-9]', '', 'g'), 1, 6));

    INSERT INTO public.profiles (id, username, full_name, avatar_url, country, province)
    VALUES (
        v_uid,
        v_final_username,
        coalesce(nullif(trim(p_full_name), ''), 'Cidadão Meu País'),
        p_avatar_url,
        coalesce(nullif(trim(p_country), ''), 'Angola'),
        coalesce(nullif(trim(p_province), ''), 'Luanda')
    )
    ON CONFLICT (id) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        avatar_url = coalesce(EXCLUDED.avatar_url, public.profiles.avatar_url),
        updated_at = NOW()
    RETURNING * INTO v_profile;

    RETURN v_profile;
END;
$$;
