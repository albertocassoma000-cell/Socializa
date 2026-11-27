-- =============================================================================
-- MEU PAÍS — Migração 004: Selo de Verificação 24h (Anúncios) & Música em Vídeos
-- =============================================================================

-- 1. Selo de verificação temporário (24h) na tabela profiles
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS verified_until TIMESTAMPTZ;

-- Proteger verified_until contra alteração arbitrária via PATCH direto do cliente
DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE USING (id = public.firebase_uid())
    WITH CHECK (
        id = public.firebase_uid()
        AND role = (SELECT p.role FROM public.profiles p WHERE p.id = public.firebase_uid())
        AND is_verified = (SELECT p.is_verified FROM public.profiles p WHERE p.id = public.firebase_uid())
        AND verified_until IS NOT DISTINCT FROM (SELECT p.verified_until FROM public.profiles p WHERE p.id = public.firebase_uid())
        AND is_suspended = (SELECT p.is_suspended FROM public.profiles p WHERE p.id = public.firebase_uid())
    );

-- RPC para atribuir o selo de verificação válido por exatamente 24 horas após assistir 5 anúncios de texto
CREATE OR REPLACE FUNCTION public.claim_daily_verified_badge(p_ads_watched INTEGER DEFAULT 5)
RETURNS public.profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid TEXT := public.firebase_uid();
    v_profile public.profiles;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Autenticação necessária';
    END IF;
    IF p_ads_watched IS NULL OR p_ads_watched < 5 THEN
        RAISE EXCEPTION 'É necessário assistir a 5 anúncios de texto para ativar o selo por 24 horas.';
    END IF;

    UPDATE public.profiles
    SET verified_until = NOW() + INTERVAL '24 hours',
        updated_at = NOW()
    WHERE id = v_uid
    RETURNING * INTO v_profile;

    RETURN v_profile;
END;
$$;

GRANT EXECUTE ON FUNCTION public.claim_daily_verified_badge(INTEGER) TO authenticated;

-- 2. Música selecionada do telemóvel em vídeos e publicações de vídeo
ALTER TABLE public.videos
    ADD COLUMN IF NOT EXISTS music_url TEXT;

ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS music_title TEXT,
    ADD COLUMN IF NOT EXISTS music_url TEXT;
