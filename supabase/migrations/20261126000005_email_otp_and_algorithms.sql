-- =============================================================================
-- SOCIALIZA — Migração 005: Email OTP (5 min) + Sistema Inteligente de Algoritmos
-- =============================================================================

-- 1. Tabela segura para códigos OTP de 6 dígitos (SMTP Gmail / Nodemailer)
-- Nunca guarda códigos em texto simples: apenas hash SHA-256 com salt + expiração 5 minutos.
CREATE TABLE IF NOT EXISTS public.email_otp_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('register', 'reset_password')),
    code_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    full_name TEXT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_otp_lookup
    ON public.email_otp_codes (lower(email), purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_otp_expires
    ON public.email_otp_codes (expires_at);

-- Bloqueio total ao cliente (apenas service_role na Edge Function email-otp pode ler/escrever)
ALTER TABLE public.email_otp_codes ENABLE ROW LEVEL SECURITY;

-- 2. Sinais de interesse por utilizador e telemetria real de conclusão/replay em vídeos
CREATE TABLE IF NOT EXISTS public.user_interest_signals (
    user_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    topic TEXT NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (weight >= 0.0 AND weight <= 100.0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, topic)
);

CREATE INDEX IF NOT EXISTS idx_user_interest_signals_user
    ON public.user_interest_signals (user_id, weight DESC);

ALTER TABLE public.user_interest_signals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_interest_signals_own" ON public.user_interest_signals;
CREATE POLICY "user_interest_signals_own" ON public.user_interest_signals
    FOR ALL USING (user_id = public.firebase_uid())
    WITH CHECK (user_id = public.firebase_uid());

ALTER TABLE public.video_views
    ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS replayed BOOLEAN NOT NULL DEFAULT FALSE;

-- Atualizar nome padrão de perfil no Socializa
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
        lower(coalesce(nullif(split_part(v_email, '@', 1), ''), 'socializa')),
        '[^a-z0-9._]', '', 'g'
    );
    IF char_length(v_base_username) < 3 THEN
        v_base_username := 'socializa';
    END IF;
    v_final_username := left(v_base_username, 22) || '_' || lower(substr(regexp_replace(v_uid, '[^a-zA-Z0-9]', '', 'g'), 1, 6));

    INSERT INTO public.profiles (id, username, full_name, avatar_url, country, province)
    VALUES (
        v_uid,
        v_final_username,
        coalesce(nullif(trim(p_full_name), ''), 'Utilizador Socializa'),
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

-- 3. RPC para atualizar interesses do utilizador com base em curtidas, comentários, buscas e vídeos vistos
CREATE OR REPLACE FUNCTION public.upsert_interest_signal(
    p_topic TEXT,
    p_delta DOUBLE PRECISION DEFAULT 1.5
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_topic TEXT := lower(trim(coalesce(p_topic, '')));
BEGIN
    IF v_uid IS NULL OR v_topic = '' THEN
        RETURN;
    END IF;

    INSERT INTO public.user_interest_signals (user_id, topic, weight, updated_at)
    VALUES (v_uid, v_topic, least(100.0, greatest(0.5, coalesce(p_delta, 1.5))), NOW())
    ON CONFLICT (user_id, topic) DO UPDATE SET
        weight = least(100.0, greatest(0.5, public.user_interest_signals.weight + coalesce(p_delta, 1.5))),
        updated_at = NOW();
END;
$$;

GRANT EXECUTE ON FUNCTION public.upsert_interest_signal(TEXT, DOUBLE PRECISION) TO authenticated;

-- 4. RPC: Algoritmo do Feed Principal (Para ti | Seguindo | Em alta)
CREATE OR REPLACE FUNCTION public.get_ranked_feed(
    p_mode TEXT DEFAULT 'for_you',
    p_limit INTEGER DEFAULT 15,
    p_offset INTEGER DEFAULT 0,
    p_province TEXT DEFAULT NULL,
    p_category TEXT DEFAULT NULL
)
RETURNS TABLE (
    post_id UUID,
    score DOUBLE PRECISION
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_user_province TEXT := 'Luanda';
BEGIN
    IF v_uid IS NOT NULL THEN
        SELECT coalesce(province, 'Luanda') INTO v_user_province
        FROM public.profiles
        WHERE id = v_uid;
    END IF;

    RETURN QUERY
    WITH ranked AS (
        SELECT
            p.id AS post_id,
            p.created_at,
            CASE
                WHEN lower(coalesce(p_mode, 'for_you')) = 'following' THEN
                    EXTRACT(EPOCH FROM p.created_at)
                WHEN lower(coalesce(p_mode, 'for_you')) = 'trending' THEN
                    (
                        (p.likes_count * 3.0) +
                        (p.comments_count * 5.0) +
                        (p.shares_count * 7.0) +
                        least(p.views_count, 5000) * 0.25
                    ) / power(greatest(1.0, (EXTRACT(EPOCH FROM (NOW() - p.created_at)) / 3600.0) + 2.0), 1.35)
                ELSE
                    -- Fórmula Para ti:
                    -- Interesse (+30), Recência (+20), Amigo/Seguido (+15), Comentários (+10), Partilhas (+15), Proximidade (+12)
                    (
                        CASE WHEN EXISTS (
                            SELECT 1 FROM public.user_interest_signals uis
                            WHERE uis.user_id = v_uid
                              AND (
                                  lower(coalesce(p.category, '')) = uis.topic
                                  OR EXISTS (SELECT 1 FROM unnest(p.hashtags) h WHERE lower(h) = uis.topic)
                              )
                        ) THEN 30.0 ELSE 0.0 END
                        + greatest(0.0, 20.0 * (1.0 - least(1.0, EXTRACT(EPOCH FROM (NOW() - p.created_at)) / 86400.0)))
                        + CASE WHEN EXISTS (
                            SELECT 1 FROM public.follows f
                            WHERE f.follower_id = v_uid AND f.following_id = p.author_id AND f.status = 'accepted'
                        ) THEN 15.0 ELSE 0.0 END
                        + least(10.0, ln(1.0 + p.comments_count) * 4.0)
                        + least(15.0, ln(1.0 + p.shares_count) * 6.0)
                        + least(8.0, ln(1.0 + p.likes_count) * 2.5)
                        + CASE WHEN p.location_label IS NOT NULL AND p.location_label ILIKE '%' || v_user_province || '%' THEN 12.0 ELSE 0.0 END
                        -- Penalidade anti-repetição do mesmo autor em janelas recentes
                        - CASE WHEN row_number() OVER (PARTITION BY p.author_id ORDER BY p.created_at DESC) > 2 THEN 20.0 ELSE 0.0 END
                    )
            END AS score
        FROM public.posts p
        JOIN public.profiles pr ON pr.id = p.author_id
        WHERE pr.is_suspended = FALSE
          AND NOT EXISTS (
              SELECT 1 FROM public.blocks b
              WHERE (b.blocker_id = v_uid AND b.blocked_id = p.author_id)
                 OR (b.blocker_id = p.author_id AND b.blocked_id = v_uid)
          )
          AND (
              p.visibility = 'public'
              OR p.author_id = v_uid
              OR (p.visibility = 'followers' AND EXISTS (
                  SELECT 1 FROM public.follows f
                  WHERE f.follower_id = v_uid AND f.following_id = p.author_id AND f.status = 'accepted'
              ))
          )
          AND (
              lower(coalesce(p_mode, 'for_you')) <> 'following'
              OR p.author_id = v_uid
              OR EXISTS (
                  SELECT 1 FROM public.follows f
                  WHERE f.follower_id = v_uid AND f.following_id = p.author_id AND f.status = 'accepted'
              )
          )
          AND (p_province IS NULL OR p_province = 'Todas' OR coalesce(p.location_label, '') ILIKE '%' || p_province || '%')
          AND (p_category IS NULL OR p.category = p_category)
    )
    SELECT r.post_id, r.score
    FROM ranked r
    ORDER BY r.score DESC, r.created_at DESC
    LIMIT greatest(1, least(coalesce(p_limit, 15), 50))
    OFFSET greatest(0, coalesce(p_offset, 0));
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_ranked_feed(TEXT, INTEGER, INTEGER, TEXT, TEXT) TO authenticated;

-- 5. RPC: Algoritmo de Recomendação de Vídeos (watch time, conclusão, replay, interações)
CREATE OR REPLACE FUNCTION public.get_recommended_videos(
    p_limit INTEGER DEFAULT 15,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    video_id UUID,
    score DOUBLE PRECISION
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
BEGIN
    RETURN QUERY
    SELECT
        v.id AS video_id,
        (
            (v.likes_count * 2.5)
            + (v.comments_count * 4.5)
            + (v.shares_count * 6.0)
            + coalesce((
                SELECT AVG(least(vv.watch_seconds, 120)) * 0.45
                     + AVG(CASE WHEN vv.completed THEN 20.0 ELSE 0.0 END)
                     + AVG(CASE WHEN vv.replayed THEN 18.0 ELSE 0.0 END)
                FROM public.video_views vv
                WHERE vv.video_id = v.id
            ), 0.0)
            + CASE WHEN EXISTS (
                SELECT 1 FROM public.user_interest_signals uis
                WHERE uis.user_id = v_uid
                  AND EXISTS (SELECT 1 FROM unnest(v.hashtags) h WHERE lower(h) = uis.topic)
            ) THEN 25.0 ELSE 0.0 END
            + greatest(0.0, 20.0 * (1.0 - least(1.0, EXTRACT(EPOCH FROM (NOW() - v.created_at)) / 172800.0)))
        ) AS score
    FROM public.videos v
    JOIN public.profiles pr ON pr.id = v.author_id
    WHERE pr.is_suspended = FALSE
      AND NOT EXISTS (
          SELECT 1 FROM public.blocks b
          WHERE (b.blocker_id = v_uid AND b.blocked_id = v.author_id)
             OR (b.blocker_id = v.author_id AND b.blocked_id = v_uid)
      )
    ORDER BY score DESC, v.created_at DESC
    LIMIT greatest(1, least(coalesce(p_limit, 15), 50))
    OFFSET greatest(0, coalesce(p_offset, 0));
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_recommended_videos(INTEGER, INTEGER) TO authenticated;

-- 6. RPC: Sugestões de Pessoas (amigos em comum, mesma província, interesses partilhados)
CREATE OR REPLACE FUNCTION public.get_suggested_people(
    p_limit INTEGER DEFAULT 20
)
RETURNS TABLE (
    user_id TEXT,
    mutual_count INTEGER,
    score DOUBLE PRECISION
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_user_province TEXT := 'Luanda';
BEGIN
    IF v_uid IS NOT NULL THEN
        SELECT coalesce(province, 'Luanda') INTO v_user_province FROM public.profiles WHERE id = v_uid;
    END IF;

    RETURN QUERY
    SELECT
        p.id AS user_id,
        coalesce((
            SELECT COUNT(*)::INTEGER
            FROM public.follows f1
            JOIN public.follows f2 ON f1.following_id = f2.follower_id
            WHERE f1.follower_id = v_uid
              AND f2.following_id = p.id
              AND f1.status = 'accepted'
              AND f2.status = 'accepted'
        ), 0) AS mutual_count,
        (
            coalesce((
                SELECT COUNT(*)::DOUBLE PRECISION * 18.0
                FROM public.follows f1
                JOIN public.follows f2 ON f1.following_id = f2.follower_id
                WHERE f1.follower_id = v_uid
                  AND f2.following_id = p.id
                  AND f1.status = 'accepted'
                  AND f2.status = 'accepted'
            ), 0.0)
            + CASE WHEN p.province = v_user_province THEN 14.0 ELSE 0.0 END
            + least(18.0, ln(1.0 + p.followers_count) * 4.0)
            + least(10.0, p.posts_count * 1.5)
        ) AS score
    FROM public.profiles p
    WHERE p.id <> v_uid
      AND p.is_suspended = FALSE
      AND NOT EXISTS (
          SELECT 1 FROM public.follows f
          WHERE f.follower_id = v_uid AND f.following_id = p.id
      )
      AND NOT EXISTS (
          SELECT 1 FROM public.blocks b
          WHERE (b.blocker_id = v_uid AND b.blocked_id = p.id)
             OR (b.blocker_id = p.id AND b.blocked_id = v_uid)
      )
    ORDER BY score DESC, p.followers_count DESC
    LIMIT greatest(1, least(coalesce(p_limit, 20), 50));
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_suggested_people(INTEGER) TO authenticated;

-- 7. RPC: Algoritmo de Tendências (hashtags em crescimento, comentários e partilhas)
CREATE OR REPLACE FUNCTION public.get_trending_topics(
    p_limit INTEGER DEFAULT 10
)
RETURNS TABLE (
    tag TEXT,
    mentions_count INTEGER,
    trend_score DOUBLE PRECISION
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    WITH tag_events AS (
        SELECT
            CASE WHEN left(trim(t), 1) = '#' THEN trim(t) ELSE '#' || trim(t) END AS tag,
            p.likes_count,
            p.comments_count,
            p.shares_count,
            p.created_at
        FROM public.posts p, unnest(p.hashtags) AS t
        WHERE p.visibility = 'public'
          AND p.created_at >= NOW() - INTERVAL '14 days'
        UNION ALL
        SELECT
            CASE WHEN left(trim(t), 1) = '#' THEN trim(t) ELSE '#' || trim(t) END AS tag,
            v.likes_count,
            v.comments_count,
            v.shares_count,
            v.created_at
        FROM public.videos v, unnest(v.hashtags) AS t
        WHERE v.created_at >= NOW() - INTERVAL '14 days'
    )
    SELECT
        min(tag) AS tag,
        COUNT(*)::INTEGER AS mentions_count,
        SUM(
            (10.0 + likes_count * 2.0 + comments_count * 4.0 + shares_count * 6.0)
            / power(greatest(1.0, (EXTRACT(EPOCH FROM (NOW() - created_at)) / 3600.0) + 2.0), 1.1)
        ) AS trend_score
    FROM tag_events
    WHERE char_length(tag) > 1
    GROUP BY lower(tag)
    ORDER BY trend_score DESC, mentions_count DESC
    LIMIT greatest(1, least(coalesce(p_limit, 10), 25));
$$;

GRANT EXECUTE ON FUNCTION public.get_trending_topics(INTEGER) TO authenticated;
