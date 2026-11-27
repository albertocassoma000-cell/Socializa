-- =============================================================================
-- MEU PAÍS — Schema Oficial PostgreSQL (Supabase)
-- Arquitetura híbrida: Supabase (Dados, Auth, Storage, Realtime, RLS)
--                      + Firebase (FCM, Analytics, Crashlytics, Remote Config)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- -----------------------------------------------------------------------------
-- 1. PROFILES (criado automaticamente ao registar no Supabase Auth)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL CHECK (char_length(username) BETWEEN 3 AND 32 AND username ~ '^[a-zA-Z0-9._]+$'),
    full_name TEXT NOT NULL DEFAULT '',
    bio TEXT NOT NULL DEFAULT '' CHECK (char_length(bio) <= 300),
    avatar_url TEXT,
    cover_url TEXT,
    country TEXT NOT NULL DEFAULT 'Angola',
    province TEXT NOT NULL DEFAULT 'Luanda',
    website TEXT,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'moderator', 'admin')),
    is_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    followers_count INTEGER NOT NULL DEFAULT 0 CHECK (followers_count >= 0),
    following_count INTEGER NOT NULL DEFAULT 0 CHECK (following_count >= 0),
    posts_count INTEGER NOT NULL DEFAULT 0 CHECK (posts_count >= 0),
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_username_trgm ON public.profiles USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_profiles_country_province ON public.profiles (country, province);

-- Trigger automático: cria perfil ao inserir utilizador em auth.users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    base_username TEXT;
    final_username TEXT;
BEGIN
    base_username := regexp_replace(
        lower(coalesce(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1), 'user')),
        '[^a-z0-9._]', '', 'g'
    );
    IF char_length(base_username) < 3 THEN
        base_username := 'cidadao_' || substr(NEW.id::text, 1, 6);
    END IF;
    final_username := left(base_username, 24) || '_' || substr(replace(NEW.id::text, '-', ''), 1, 4);

    INSERT INTO public.profiles (id, username, full_name, avatar_url)
    VALUES (
        NEW.id,
        final_username,
        coalesce(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', 'Utilizador Meu País'),
        NEW.raw_user_meta_data->>'avatar_url'
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- -----------------------------------------------------------------------------
-- 2. POSTS, MEDIA, LIKES, COMMENTS, FOLLOWS
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_type TEXT NOT NULL DEFAULT 'photo' CHECK (post_type IN ('text', 'photo', 'video', 'carousel')),
    caption TEXT NOT NULL DEFAULT '' CHECK (char_length(caption) <= 2200),
    hashtags TEXT[] NOT NULL DEFAULT '{}',
    location_label TEXT,
    visibility TEXT NOT NULL DEFAULT 'public' CHECK (visibility IN ('public', 'followers', 'private')),
    allow_comments BOOLEAN NOT NULL DEFAULT TRUE,
    likes_count INTEGER NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    comments_count INTEGER NOT NULL DEFAULT 0 CHECK (comments_count >= 0),
    shares_count INTEGER NOT NULL DEFAULT 0 CHECK (shares_count >= 0),
    saves_count INTEGER NOT NULL DEFAULT 0 CHECK (saves_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_posts_author_created ON public.posts (author_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_hashtags ON public.posts USING gin (hashtags);
CREATE INDEX IF NOT EXISTS idx_posts_created ON public.posts (created_at DESC);

CREATE TABLE IF NOT EXISTS public.media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID REFERENCES public.posts(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL CHECK (media_type IN ('image', 'video', 'audio', 'document')),
    storage_bucket TEXT NOT NULL DEFAULT 'media',
    storage_path TEXT NOT NULL,
    public_url TEXT NOT NULL,
    thumbnail_url TEXT,
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_media_post ON public.media (post_id, sort_order);

CREATE TABLE IF NOT EXISTS public.likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    reaction TEXT NOT NULL DEFAULT 'like' CHECK (reaction IN ('like', 'love', 'fire', 'applause')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, post_id)
);

CREATE TABLE IF NOT EXISTS public.saved_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, post_id)
);

CREATE TABLE IF NOT EXISTS public.comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES public.comments(id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    likes_count INTEGER NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comments_post_created ON public.comments (post_id, created_at ASC);

CREATE TABLE IF NOT EXISTS public.follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'accepted' CHECK (status IN ('pending', 'accepted')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (follower_id <> following_id),
    UNIQUE (follower_id, following_id)
);

CREATE INDEX IF NOT EXISTS idx_follows_following ON public.follows (following_id, status);

-- -----------------------------------------------------------------------------
-- 3. STORIES (24 HORAS)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    media_type TEXT NOT NULL DEFAULT 'image' CHECK (media_type IN ('image', 'video')),
    caption TEXT CHECK (char_length(caption) <= 300),
    highlight_label TEXT,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stories_active ON public.stories (expires_at DESC, author_id);

CREATE TABLE IF NOT EXISTS public.story_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES public.stories(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (story_id, viewer_id)
);

-- -----------------------------------------------------------------------------
-- 4. VIDEOS (REELS / STREAM) & LIVE STREAMS
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    provider TEXT NOT NULL DEFAULT 'cloudflare_stream' CHECK (provider IN ('cloudflare_stream', 'mux', 'supabase_storage')),
    stream_asset_id TEXT NOT NULL,
    playback_url TEXT NOT NULL,
    thumbnail_url TEXT,
    caption TEXT NOT NULL DEFAULT '',
    hashtags TEXT[] NOT NULL DEFAULT '{}',
    music_title TEXT NOT NULL DEFAULT 'Som original',
    effects_tags TEXT[] NOT NULL DEFAULT '{}',
    captions_vtt_url TEXT,
    duet_source_video_id UUID REFERENCES public.videos(id) ON DELETE SET NULL,
    reply_to_video_id UUID REFERENCES public.videos(id) ON DELETE SET NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    likes_count INTEGER NOT NULL DEFAULT 0,
    comments_count INTEGER NOT NULL DEFAULT 0,
    shares_count INTEGER NOT NULL DEFAULT 0,
    views_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.video_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES public.videos(id) ON DELETE CASCADE,
    viewer_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    watch_seconds INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.live_streams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    livekit_room_name TEXT UNIQUE NOT NULL,
    playback_url TEXT,
    status TEXT NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'live', 'ended')),
    viewer_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 5. GRUPOS, CONVERSAS, MENSAGENS (REALTIME)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 2 AND 80),
    description TEXT NOT NULL DEFAULT '',
    cover_url TEXT,
    province TEXT,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    members_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group BOOLEAN NOT NULL DEFAULT FALSE,
    group_id UUID REFERENCES public.groups(id) ON DELETE SET NULL,
    title TEXT,
    avatar_url TEXT,
    last_message_preview TEXT,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.conversation_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('admin', 'member')),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    message_type TEXT NOT NULL DEFAULT 'text' CHECK (message_type IN ('text', 'image', 'audio', 'document', 'call_event')),
    body TEXT NOT NULL DEFAULT '',
    attachment_url TEXT,
    attachment_name TEXT,
    delivery_status TEXT NOT NULL DEFAULT 'sent' CHECK (delivery_status IN ('sent', 'delivered', 'read')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON public.messages (conversation_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 6. NOTIFICAÇÕES & DEVICE PUSH TOKENS (FCM)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.device_push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT NOT NULL CHECK (platform IN ('android', 'ios', 'web')),
    device_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON public.device_push_tokens (user_id);

CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('follow', 'like', 'comment', 'message', 'mention', 'invite', 'live_stream', 'system')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON public.notifications (recipient_id, is_read, created_at DESC);

-- -----------------------------------------------------------------------------
-- 7. MARKETPLACE, PUBLICIDADE, SUBSCRIÇÕES & TRANSAÇÕES
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (char_length(title) BETWEEN 2 AND 120),
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Eletrónicos',
    price_kz NUMERIC(14, 2) NOT NULL CHECK (price_kz >= 0),
    currency TEXT NOT NULL DEFAULT 'AOA',
    province TEXT NOT NULL DEFAULT 'Luanda',
    image_url TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'sold', 'paused', 'removed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.advertisements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advertiser_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    media_url TEXT NOT NULL,
    target_url TEXT NOT NULL,
    budget_kz NUMERIC(14, 2) NOT NULL DEFAULT 0,
    impressions INTEGER NOT NULL DEFAULT 0,
    clicks INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'paused', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    plan_code TEXT NOT NULL,
    amount_kz NUMERIC(12, 2) NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled', 'expired')),
    renews_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    provider TEXT NOT NULL DEFAULT 'multicaixa_express',
    external_ref TEXT UNIQUE,
    amount_kz NUMERIC(14, 2) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'AOA',
    tx_type TEXT NOT NULL CHECK (tx_type IN ('marketplace', 'subscription', 'ad_spend', 'creator_tip')),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'succeeded', 'failed', 'refunded')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 8. MODERAÇÃO, DENÚNCIAS, BLOQUEIOS & AUDITORIA
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (blocker_id <> blocked_id),
    UNIQUE (blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('post', 'comment', 'user', 'video', 'product', 'message')),
    target_id UUID NOT NULL,
    reason TEXT NOT NULL,
    details TEXT,
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'reviewing', 'resolved', 'dismissed')),
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.audit_logs (created_at DESC);

-- =============================================================================
-- ROW LEVEL SECURITY (RLS)
-- =============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.videos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_streams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_push_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.advertisements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- Função auxiliar de verificação administrativa no servidor
CREATE OR REPLACE FUNCTION public.is_admin_or_mod()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role IN ('admin', 'moderator') AND is_suspended = FALSE
    );
$$;

-- Perfis: leitura pública para contas ativas (ou próprio perfil), edição apenas pelo dono
CREATE POLICY "profiles_read_all" ON public.profiles
    FOR SELECT USING (is_suspended = FALSE OR id = auth.uid() OR public.is_admin_or_mod());
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- Posts: respeitar visibilidade + bloqueios
CREATE POLICY "posts_read_visible" ON public.posts
    FOR SELECT USING (
        NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE (b.blocker_id = auth.uid() AND b.blocked_id = author_id)
               OR (b.blocker_id = author_id AND b.blocked_id = auth.uid())
        )
        AND (
            visibility = 'public'
            OR author_id = auth.uid()
            OR (visibility = 'followers' AND EXISTS (
                SELECT 1 FROM public.follows f
                WHERE f.follower_id = auth.uid() AND f.following_id = author_id AND f.status = 'accepted'
            ))
            OR public.is_admin_or_mod()
        )
    );
CREATE POLICY "posts_insert_own" ON public.posts
    FOR INSERT WITH CHECK (auth.uid() = author_id);
CREATE POLICY "posts_update_own" ON public.posts
    FOR UPDATE USING (auth.uid() = author_id OR public.is_admin_or_mod());
CREATE POLICY "posts_delete_own" ON public.posts
    FOR DELETE USING (auth.uid() = author_id OR public.is_admin_or_mod());

-- Likes, Comments, Saved Posts, Follows
CREATE POLICY "likes_read" ON public.likes FOR SELECT USING (TRUE);
CREATE POLICY "likes_write_own" ON public.likes FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "saved_posts_own" ON public.saved_posts FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "comments_read" ON public.comments FOR SELECT USING (TRUE);
CREATE POLICY "comments_insert_own" ON public.comments FOR INSERT WITH CHECK (auth.uid() = author_id);
CREATE POLICY "comments_delete_own" ON public.comments FOR DELETE USING (auth.uid() = author_id OR public.is_admin_or_mod());

CREATE POLICY "follows_read" ON public.follows FOR SELECT USING (TRUE);
CREATE POLICY "follows_manage_own" ON public.follows FOR ALL USING (auth.uid() = follower_id) WITH CHECK (auth.uid() = follower_id);

-- Stories (24h)
CREATE POLICY "stories_read_active" ON public.stories FOR SELECT USING (expires_at > NOW() OR author_id = auth.uid());
CREATE POLICY "stories_insert_own" ON public.stories FOR INSERT WITH CHECK (auth.uid() = author_id);
CREATE POLICY "stories_delete_own" ON public.stories FOR DELETE USING (auth.uid() = author_id);
CREATE POLICY "story_views_own" ON public.story_views FOR ALL USING (auth.uid() = viewer_id) WITH CHECK (auth.uid() = viewer_id);

-- Conversas & Mensagens: apenas membros da conversa
CREATE POLICY "conversations_member_read" ON public.conversations
    FOR SELECT USING (EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = id AND cm.user_id = auth.uid()
    ));
CREATE POLICY "conversation_members_read" ON public.conversation_members
    FOR SELECT USING (user_id = auth.uid() OR EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = auth.uid()
    ));
CREATE POLICY "messages_member_read" ON public.messages
    FOR SELECT USING (EXISTS (
        SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = auth.uid()
    ));
CREATE POLICY "messages_member_insert" ON public.messages
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id
        AND EXISTS (
            SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = conversation_id AND cm.user_id = auth.uid()
        )
    );

-- Device Push Tokens & Notificações: estritamente do utilizador autenticado
CREATE POLICY "push_tokens_own" ON public.device_push_tokens
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "notifications_own" ON public.notifications
    FOR SELECT USING (auth.uid() = recipient_id);
CREATE POLICY "notifications_update_own" ON public.notifications
    FOR UPDATE USING (auth.uid() = recipient_id) WITH CHECK (auth.uid() = recipient_id);

-- Marketplace & Vídeos
CREATE POLICY "videos_read_all" ON public.videos FOR SELECT USING (TRUE);
CREATE POLICY "videos_insert_own" ON public.videos FOR INSERT WITH CHECK (auth.uid() = author_id);
CREATE POLICY "products_read_active" ON public.products FOR SELECT USING (status = 'active' OR seller_id = auth.uid() OR public.is_admin_or_mod());
CREATE POLICY "products_manage_own" ON public.products FOR ALL USING (auth.uid() = seller_id) WITH CHECK (auth.uid() = seller_id);

-- Reports, Blocks & Audit Logs
CREATE POLICY "blocks_own" ON public.blocks FOR ALL USING (auth.uid() = blocker_id) WITH CHECK (auth.uid() = blocker_id);
CREATE POLICY "reports_insert_auth" ON public.reports FOR INSERT WITH CHECK (auth.uid() = reporter_id);
CREATE POLICY "reports_admin_read" ON public.reports FOR SELECT USING (auth.uid() = reporter_id OR public.is_admin_or_mod());
CREATE POLICY "reports_admin_update" ON public.reports FOR UPDATE USING (public.is_admin_or_mod());
CREATE POLICY "audit_logs_admin_only" ON public.audit_logs FOR SELECT USING (public.is_admin_or_mod());
