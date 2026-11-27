// Supabase Edge Function: email-otp
// Envia códigos OTP de 6 dígitos via SMTP Gmail (Nodemailer) com validade de 5 minutos,
// realiza registo (Nome, Email, Senha) e recuperação de senha sem links no Firebase Auth.
// Todas as credenciais SMTP e Service Account ficam apenas nos Secrets do servidor.

import nodemailer from "npm:nodemailer@6.9.16";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { importPKCS8, SignJWT } from "https://esm.sh/jose@5.9.6";

const OTP_TTL_MS = 5 * 60 * 1000; // 5 minutos exatos
const RESEND_COOLDOWN_MS = 45 * 1000;
const MAX_ATTEMPTS = 5;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function normalizeEmail(raw: unknown): string {
  return typeof raw === "string" ? raw.trim().toLowerCase() : "";
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function generateSixDigitOtp(): string {
  const arr = new Uint32Array(1);
  crypto.getRandomValues(arr);
  return String(arr[0] % 1_000_000).padStart(6, "0");
}

function generateSalt(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

interface ServiceAccountCreds {
  project_id: string;
  client_email: string;
  private_key: string;
}

function getFirebaseServiceAccount(): ServiceAccountCreds | null {
  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")?.trim();
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed?.client_email || !parsed?.private_key || !parsed?.project_id) {
      return null;
    }
    return parsed as ServiceAccountCreds;
  } catch {
    return null;
  }
}

async function getGoogleAdminAccessToken(sa: ServiceAccountCreds): Promise<string> {
  const nowSec = Math.floor(Date.now() / 1000);
  const key = await importPKCS8(sa.private_key, "RS256");
  const assertion = await new SignJWT({
    scope: "https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/firebase",
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setSubject(sa.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(nowSec)
    .setExpirationTime(nowSec + 3000)
    .sign(key);

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenRes.ok) {
    throw new Error(`Falha OAuth Firebase Admin (${tokenRes.status})`);
  }
  const tokenJson = await tokenRes.json();
  return tokenJson.access_token as string;
}

async function firebaseAdminRequest(
  sa: ServiceAccountCreds,
  accessToken: string,
  endpoint: string,
  payload: Record<string, unknown>
): Promise<{ ok: boolean; status: number; data: Record<string, unknown> }> {
  const url = `https://identitytoolkit.googleapis.com/v1/projects/${sa.project_id}/${endpoint}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data };
}

async function sendOtpEmailViaGmailSmtp(
  email: string,
  otp: string,
  purpose: "register" | "reset_password",
  fullName?: string
): Promise<void> {
  const smtpUser = Deno.env.get("GMAIL_SMTP_USER")?.trim();
  const smtpPass = Deno.env.get("GMAIL_SMTP_APP_PASSWORD")?.trim();
  if (!smtpUser || !smtpPass) {
    throw new Error("SMTP_NOT_CONFIGURED");
  }

  const transporter = nodemailer.createTransport({
    host: "smtp.gmail.com",
    port: 465,
    secure: true,
    auth: {
      user: smtpUser,
      pass: smtpPass,
    },
  });

  const subject =
    purpose === "register"
      ? "Socializa — Código de confirmação da conta"
      : "Socializa — Código para recuperar a tua senha";

  const greeting = fullName ? `Olá ${fullName},` : "Olá,";
  const actionLine =
    purpose === "register"
      ? "Usa o código abaixo para confirmar o teu registo na app Socializa:"
      : "Usa o código abaixo na app Socializa para definir uma nova senha:";

  await transporter.sendMail({
    from: `"Socializa" <${smtpUser}>`,
    to: email,
    subject,
    text: `${greeting}\n\n${actionLine}\n\nCódigo: ${otp}\n\nEste código é válido durante 5 minutos. Não o partilhes com ninguém.`,
    html: `
      <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 460px; margin: 0 auto; padding: 24px; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px;">
        <h2 style="margin: 0 0 12px; color: #0b1220; font-size: 20px;">Socializa</h2>
        <p style="margin: 0 0 12px; color: #334155; font-size: 15px;">${greeting}</p>
        <p style="margin: 0 0 18px; color: #334155; font-size: 15px;">${actionLine}</p>
        <div style="background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 10px; padding: 16px; text-align: center; letter-spacing: 8px; font-size: 30px; font-weight: 700; color: #ff6b00;">
          ${otp}
        </div>
        <p style="margin: 18px 0 0; color: #64748b; font-size: 13px;">
          Válido durante <strong>5 minutos</strong>. Se não pediste este código, ignora este e-mail.
        </p>
      </div>
    `,
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "Método não permitido." }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() ?? "";
  if (!supabaseUrl || !serviceRoleKey) {
    return json(
      {
        code: "BACKEND_NOT_CONFIGURED",
        error: "SUPABASE_URL ou SUPABASE_SERVICE_ROLE_KEY ausente na Edge Function.",
      },
      503
    );
  }

  const smtpUser = Deno.env.get("GMAIL_SMTP_USER")?.trim() ?? "";
  const smtpPass = Deno.env.get("GMAIL_SMTP_APP_PASSWORD")?.trim() ?? "";
  if (!smtpUser || !smtpPass) {
    return json(
      {
        code: "SMTP_NOT_CONFIGURED",
        error: "Configura GMAIL_SMTP_USER e GMAIL_SMTP_APP_PASSWORD nos secrets do Supabase para enviar códigos por e-mail.",
      },
      503
    );
  }

  const sa = getFirebaseServiceAccount();
  if (!sa) {
    return json(
      {
        code: "FIREBASE_ADMIN_NOT_CONFIGURED",
        error: "Configura FIREBASE_SERVICE_ACCOUNT_JSON nos secrets do Supabase para autenticar contas por e-mail e senha.",
      },
      503
    );
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Corpo JSON inválido." }, 400);
  }

  const action = typeof body.action === "string" ? body.action.trim() : "";
  const email = normalizeEmail(body.email);
  if (!isValidEmail(email)) {
    return json({ error: "Introduz um endereço de e-mail válido." }, 400);
  }

  const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey);

  try {
    const adminToken = await getGoogleAdminAccessToken(sa);

    if (action === "send_register_otp" || action === "send_reset_otp") {
      const purpose = action === "send_register_otp" ? "register" : "reset_password";
      const fullName = typeof body.full_name === "string" ? body.full_name.trim() : "";

      if (purpose === "register" && fullName.length < 2) {
        return json({ error: "Indica o teu nome completo para criar conta." }, 400);
      }

      // Verifica existência da conta no Firebase Auth
      const lookup = await firebaseAdminRequest(sa, adminToken, "accounts:lookup", {
        email: [email],
      });
      const existingUsers = Array.isArray(lookup.data?.users) ? lookup.data.users : [];
      if (purpose === "register" && existingUsers.length > 0) {
        return json({ error: "Este e-mail já está registado. Inicia sessão ou recupera a tua senha." }, 409);
      }
      if (purpose === "reset_password" && existingUsers.length === 0) {
        return json({ error: "Nenhuma conta encontrada com este e-mail." }, 404);
      }

      // Evita spam no reenvio (cooldown de 45s)
      const { data: latest } = await supabaseAdmin
        .from("email_otp_codes")
        .select("id, created_at, consumed_at")
        .eq("email", email)
        .eq("purpose", purpose)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (latest && !latest.consumed_at) {
        const ageMs = Date.now() - new Date(latest.created_at).getTime();
        if (ageMs < RESEND_COOLDOWN_MS) {
          const waitSec = Math.ceil((RESEND_COOLDOWN_MS - ageMs) / 1000);
          return json(
            {
              error: `Aguarda ${waitSec}s antes de pedir um novo código.`,
              retry_after_seconds: waitSec,
            },
            429
          );
        }
      }

      const otp = generateSixDigitOtp();
      const salt = generateSalt();
      const codeHash = await sha256Hex(`${salt}:${otp}`);
      const expiresAt = new Date(Date.now() + OTP_TTL_MS).toISOString();

      // Invalida códigos anteriores não consumidos deste e-mail + finalidade
      await supabaseAdmin
        .from("email_otp_codes")
        .update({ consumed_at: new Date().toISOString() })
        .eq("email", email)
        .eq("purpose", purpose)
        .is("consumed_at", null);

      const { error: insertErr } = await supabaseAdmin.from("email_otp_codes").insert({
        email,
        purpose,
        code_hash: codeHash,
        salt,
        full_name: fullName || null,
        expires_at: expiresAt,
      });
      if (insertErr) {
        return json({ error: `Erro ao preparar código OTP: ${insertErr.message}` }, 500);
      }

      await sendOtpEmailViaGmailSmtp(email, otp, purpose, fullName || undefined);

      return json({
        ok: true,
        message: "Código de 6 dígitos enviado para o teu e-mail (válido por 5 minutos).",
        expires_in_seconds: 300,
      });
    }

    if (action === "verify_register_otp" || action === "verify_reset_otp") {
      const purpose = action === "verify_register_otp" ? "register" : "reset_password";
      const otp = typeof body.otp === "string" ? body.otp.trim() : "";
      const password =
        typeof body.password === "string"
          ? body.password
          : typeof body.new_password === "string"
          ? body.new_password
          : "";
      const fullName = typeof body.full_name === "string" ? body.full_name.trim() : "";

      if (!/^\d{6}$/.test(otp)) {
        return json({ error: "Introduz o código de 6 dígitos." }, 400);
      }
      if (password.length < 6) {
        return json({ error: "A senha deve ter pelo menos 6 caracteres." }, 400);
      }

      const { data: record, error: fetchErr } = await supabaseAdmin
        .from("email_otp_codes")
        .select("*")
        .eq("email", email)
        .eq("purpose", purpose)
        .is("consumed_at", null)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (fetchErr || !record) {
        return json({ error: "Nenhum código ativo encontrado. Pede um novo código." }, 400);
      }
      if (new Date(record.expires_at).getTime() < Date.now()) {
        await supabaseAdmin
          .from("email_otp_codes")
          .update({ consumed_at: new Date().toISOString() })
          .eq("id", record.id);
        return json({ error: "O código expirou (5 minutos). Pede um novo código." }, 410);
      }
      if (record.attempts >= MAX_ATTEMPTS) {
        return json({ error: "Demasiadas tentativas erradas. Pede um novo código." }, 429);
      }

      const candidateHash = await sha256Hex(`${record.salt}:${otp}`);
      if (!constantTimeEquals(candidateHash, record.code_hash)) {
        await supabaseAdmin
          .from("email_otp_codes")
          .update({ attempts: record.attempts + 1 })
          .eq("id", record.id);
        return json({ error: "Código incorreto. Verifica o teu e-mail e tenta novamente." }, 400);
      }

      // Código válido -> marca como consumido
      await supabaseAdmin
        .from("email_otp_codes")
        .update({ consumed_at: new Date().toISOString() })
        .eq("id", record.id);

      if (purpose === "register") {
        const resolvedName = fullName || record.full_name || "Utilizador Socializa";
        const createRes = await firebaseAdminRequest(sa, adminToken, "accounts", {
          email,
          password,
          displayName: resolvedName,
          emailVerified: true,
        });
        if (!createRes.ok) {
          const fbMsg = (createRes.data?.error as Record<string, unknown>)?.message ?? "Erro ao criar conta Firebase";
          return json({ error: String(fbMsg) }, 400);
        }
        const uid = String(createRes.data?.localId ?? "");
        if (uid) {
          const baseUser = email
            .split("@")[0]
            .toLowerCase()
            .replace(/[^a-z0-9._]/g, "")
            .slice(0, 20) || "socializa";
          const username = `${baseUser}_${uid.slice(0, 5).toLowerCase()}`;
          await supabaseAdmin.from("profiles").upsert(
            {
              id: uid,
              username,
              full_name: resolvedName,
              country: "Angola",
              province: "Luanda",
            },
            { onConflict: "id" }
          );
        }
        return json({
          ok: true,
          uid,
          email,
          message: "Conta confirmada com sucesso!",
        });
      } else {
        // Recuperação de senha por código (sem links)
        const lookup = await firebaseAdminRequest(sa, adminToken, "accounts:lookup", {
          email: [email],
        });
        const target = Array.isArray(lookup.data?.users) ? (lookup.data.users[0] as Record<string, unknown>) : null;
        const uid = target?.localId ? String(target.localId) : "";
        if (!uid) {
          return json({ error: "Conta não encontrada no Firebase Auth." }, 404);
        }
        const updateRes = await firebaseAdminRequest(sa, adminToken, "accounts:update", {
          localId: uid,
          password,
          emailVerified: true,
        });
        if (!updateRes.ok) {
          const fbMsg = (updateRes.data?.error as Record<string, unknown>)?.message ?? "Erro ao atualizar senha";
          return json({ error: String(fbMsg) }, 400);
        }
        return json({
          ok: true,
          email,
          message: "Senha redefinida com sucesso! Já podes entrar.",
        });
      }
    }

    return json({ error: "Ação desconhecida." }, 400);
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    if (msg === "SMTP_NOT_CONFIGURED") {
      return json(
        {
          code: "SMTP_NOT_CONFIGURED",
          error: "Configura GMAIL_SMTP_USER e GMAIL_SMTP_APP_PASSWORD nos secrets do Supabase.",
        },
        503
      );
    }
    return json({ error: msg }, 500);
  }
});
