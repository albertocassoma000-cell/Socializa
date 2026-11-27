# Configuração Firebase Auth + Supabase + Email OTP (Socializa)

Para evitar erros **HTTP 401** no Supabase PostgREST (`rest/v1/*`), Storage e Realtime, e ativar o envio de códigos OTP por **SMTP Gmail (Nodemailer)**, configura os itens abaixo.

## 1. Secrets na AI Studio (Frontend Android)
No painel **Secrets** do AI Studio, define (apenas chaves públicas do cliente — nunca passwords SMTP):
- `SUPABASE_URL` — ex.: `https://<project-ref>.supabase.co`
- `SUPABASE_ANON_KEY` — a chave pública **anon** do projeto Supabase

## 2. Migrações SQL
Executa as migrações na ordem numérica no SQL Editor ou via Supabase CLI (`supabase db push`):
1. `supabase/migrations/20261122000001_meu_pais_core_schema.sql`
2. `supabase/migrations/20261122000002_firebase_third_party_auth_rls.sql`
3. `supabase/migrations/20261122000003_realtime_scale_interactions.sql`
4. `supabase/migrations/20261122000004_ads_badge_and_video_music.sql`
5. `supabase/migrations/20261126000005_email_otp_and_algorithms.sql`

## 3. Edge Functions (`firebase-session`, `email-otp`, `feed-ranker`)
Faz o deploy das funções:
```bash
supabase functions deploy firebase-session --no-verify-jwt
supabase functions deploy email-otp --no-verify-jwt
supabase functions deploy feed-ranker --no-verify-jwt
```

Configura os secrets no servidor Supabase (**nunca** no código Android):
```bash
supabase secrets set \
  SUPABASE_JWT_SECRET="<teu-jwt-secret>" \
  FIREBASE_PROJECT_ID="gen-lang-client-0990149874" \
  GMAIL_SMTP_USER="<teu-email-gmail>" \
  GMAIL_SMTP_APP_PASSWORD="<password-de-aplicacao-google-16-caracteres>" \
  FIREBASE_SERVICE_ACCOUNT_JSON='<json-da-conta-de-servico-firebase>'
```
