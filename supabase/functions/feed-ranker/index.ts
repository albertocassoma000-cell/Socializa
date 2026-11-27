// Supabase Edge Function: feed-ranker
// Calcula rankings para Feed (Para ti / Seguindo / Em alta), Vídeos, Pessoas,
// Tendências e validação anti-spam no servidor.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

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

const PROHIBITED_TERMS = [
  "ganha dinheiro rápido grátis",
  "clica no link fraudulento",
  "transferência duplicada garantida",
  "phishing",
  "scam",
];

function evaluateSafety(text: string): { allowed: boolean; reason?: string } {
  const clean = text.trim().toLowerCase();
  if (!clean) return { allowed: true };
  if (/(.)\1{14,}/.test(clean)) {
    return { allowed: false, reason: "Conteúdo detetado como repetição/spam." };
  }
  for (const term of PROHIBITED_TERMS) {
    if (clean.includes(term)) {
      return { allowed: false, reason: "Publicação bloqueada pela moderação de segurança." };
    }
  }
  const urlMatches = clean.match(/https?:\/\//g) ?? [];
  if (urlMatches.length > 4) {
    return { allowed: false, reason: "Demasiados links suspeitos na mesma publicação." };
  }
  return { allowed: true };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")?.trim() ?? "";
  const authHeader = req.headers.get("Authorization") ?? "";
  if (!supabaseUrl || !anonKey) {
    return json({ error: "Supabase env not configured" }, 503);
  }

  const client = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authHeader } },
  });

  const payload = await req.json().catch(() => ({}));
  const action = String(payload?.action ?? "rank_feed");

  if (action === "moderate_content") {
    const result = evaluateSafety(String(payload?.text ?? ""));
    return json(result, result.allowed ? 200 : 422);
  }

  if (action === "trending_topics") {
    const { data, error } = await client.rpc("get_trending_topics", {
      p_limit: Number(payload?.limit ?? 10),
    });
    if (error) return json({ error: error.message }, 400);
    return json({ items: data ?? [] });
  }

  if (action === "suggest_people") {
    const { data, error } = await client.rpc("get_suggested_people", {
      p_limit: Number(payload?.limit ?? 20),
    });
    if (error) return json({ error: error.message }, 400);
    return json({ items: data ?? [] });
  }

  if (action === "rank_videos") {
    const { data, error } = await client.rpc("get_recommended_videos", {
      p_limit: Number(payload?.limit ?? 15),
      p_offset: Number(payload?.offset ?? 0),
    });
    if (error) return json({ error: error.message }, 400);
    return json({ items: data ?? [] });
  }

  const { data, error } = await client.rpc("get_ranked_feed", {
    p_mode: String(payload?.mode ?? "for_you"),
    p_limit: Number(payload?.limit ?? 15),
    p_offset: Number(payload?.offset ?? 0),
    p_province: payload?.province ?? null,
    p_category: payload?.category ?? null,
  });
  if (error) return json({ error: error.message }, 400);
  return json({ items: data ?? [] });
});
