// Supabase Edge Function: firebase-session
// Verifica criptograficamente o Firebase ID Token usando as chaves públicas JWKS da Google
// e emite um JWT Supabase de curta duração com role="authenticated" e sub=<firebase_uid>,
// garantindo compatibilidade total com RLS mesmo quando o projeto Firebase está no plano Spark
// (onde Cloud Functions não estão disponíveis para adicionar custom claims role="authenticated").

import { createRemoteJWKSet, jwtVerify, SignJWT } from "https://esm.sh/jose@5.9.6";

const FIREBASE_PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") || "gen-lang-client-0990149874";
const FIREBASE_ISSUER = `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`;
const GOOGLE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-firebase-token",
      },
    });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  const headerToken = (req.headers.get("x-firebase-token") ?? "").trim();
  const authHeader = req.headers.get("Authorization") ?? "";
  const bearerToken = authHeader.startsWith("Bearer ")
    ? authHeader.slice("Bearer ".length).trim()
    : "";
  let bodyToken = "";
  try {
    const rawBody = await req.text();
    if (rawBody) {
      const parsed = JSON.parse(rawBody);
      if (typeof parsed?.firebase_token === "string") {
        bodyToken = parsed.firebase_token.trim();
      }
    }
  } catch {
    // Corpo JSON opcional
  }

  const firebaseToken = headerToken || bodyToken || bearerToken;
  if (!firebaseToken) {
    return new Response(JSON.stringify({ error: "Missing Firebase token" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }

  const supabaseJwtSecret = Deno.env.get("SUPABASE_JWT_SECRET") ?? Deno.env.get("JWT_SECRET") ?? "";
  if (!supabaseJwtSecret) {
    return new Response(
      JSON.stringify({
        error: "SUPABASE_JWT_SECRET not configured on Edge Function secrets",
        hint: "Set SUPABASE_JWT_SECRET or configure Firebase role='authenticated' custom claim with Supabase Third-Party Auth.",
      }),
      { status: 501, headers: { "Content-Type": "application/json" } }
    );
  }

  try {
    const { payload } = await jwtVerify(firebaseToken, GOOGLE_JWKS, {
      issuer: FIREBASE_ISSUER,
      audience: FIREBASE_PROJECT_ID,
    });

    const uid = typeof payload.sub === "string" ? payload.sub.trim() : "";
    if (!uid) {
      return new Response(JSON.stringify({ error: "Firebase token missing subject (sub)" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const ttlSeconds = 55 * 60; // 55 minutos
    const nowSec = Math.floor(Date.now() / 1000);
    const signingKey = new TextEncoder().encode(supabaseJwtSecret);

    const supabaseJwt = await new SignJWT({
      sub: uid,
      role: "authenticated",
      aud: "authenticated",
      email: typeof payload.email === "string" ? payload.email : undefined,
      firebase_uid: uid,
    })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setIssuedAt(nowSec)
      .setExpirationTime(nowSec + ttlSeconds)
      .sign(signingKey);

    return new Response(
      JSON.stringify({
        access_token: supabaseJwt,
        token_type: "bearer",
        expires_in: ttlSeconds,
        user_id: uid,
      }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({
        error: "Invalid or expired Firebase ID Token",
        details: err instanceof Error ? err.message : String(err),
      }),
      { status: 401, headers: { "Content-Type": "application/json" } }
    );
  }
});
