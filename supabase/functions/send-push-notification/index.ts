// Supabase Edge Function: send-push-notification
// Valida a identidade autenticada via Firebase JWT + Supabase Third-Party Auth + RLS
// e envia notificações via FCM HTTP v1 com credenciais protegidas no servidor.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface PushRequest {
  recipient_id: string;
  event_type: "follow" | "like" | "comment" | "comment_reply" | "message" | "mention" | "invite" | "live_stream";
  title: string;
  body: string;
  reference_id?: string;
  dispatch_only?: boolean;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const fcmProjectId = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
  const fcmAccessToken = Deno.env.get("FIREBASE_SERVER_ACCESS_TOKEN") ?? "";

  const adminClient = createClient(supabaseUrl, serviceRoleKey);
  const userClient = createClient(supabaseUrl, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });

  // Valida o token Firebase através do Supabase PostgREST + função firebase_uid()
  const { data: callerUid, error: uidError } = await userClient.rpc("firebase_uid");
  if (uidError || !callerUid) {
    return new Response(JSON.stringify({ error: "Invalid Firebase JWT" }), { status: 401 });
  }

  const payload = (await req.json()) as PushRequest;
  if (!payload.recipient_id || payload.recipient_id === callerUid) {
    return new Response(JSON.stringify({ delivered: 0, skipped: true }), {
      headers: { "Content-Type": "application/json" },
    });
  }

  if (!payload.dispatch_only) {
    await adminClient.from("notifications").insert({
      recipient_id: payload.recipient_id,
      actor_id: callerUid as string,
      event_type: payload.event_type,
      title: payload.title,
      body: payload.body,
      reference_id: payload.reference_id ?? null,
    });
  }

  const { data: tokens } = await adminClient
    .from("device_push_tokens")
    .select("id, token, platform")
    .eq("user_id", payload.recipient_id);

  if (!tokens || tokens.length === 0 || !fcmProjectId || !fcmAccessToken) {
    return new Response(JSON.stringify({ delivered: 0, queued: true }), {
      headers: { "Content-Type": "application/json" },
    });
  }

  const invalidTokenIds: string[] = [];
  let delivered = 0;

  for (const row of tokens) {
    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${fcmProjectId}/messages:send`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${fcmAccessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: row.token,
            notification: { title: payload.title, body: payload.body },
            data: {
              event_type: payload.event_type,
              reference_id: payload.reference_id ?? "",
            },
          },
        }),
      }
    );

    if (fcmRes.ok) {
      delivered++;
    } else if (fcmRes.status === 404 || fcmRes.status === 400) {
      invalidTokenIds.push(row.id);
    }
  }

  if (invalidTokenIds.length > 0) {
    await adminClient.from("device_push_tokens").delete().in("id", invalidTokenIds);
  }

  return new Response(
    JSON.stringify({ delivered, pruned: invalidTokenIds.length }),
    { headers: { "Content-Type": "application/json" } }
  );
});
