// Supabase Edge Function: admin-moderate
// Valida o Firebase ID Token via Supabase Third-Party Auth (firebase_uid)
// e verifica permissões administrativas no PostgreSQL antes de moderar.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface ModerationCommand {
  report_id?: string;
  action: "resolve_report" | "dismiss_report" | "suspend_user" | "unsuspend_user" | "remove_post";
  target_id: string;
  reason?: string;
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
  const adminClient = createClient(supabaseUrl, serviceRoleKey);
  const userClient = createClient(supabaseUrl, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });

  const { data: callerUid, error: uidError } = await userClient.rpc("firebase_uid");
  if (uidError || !callerUid) {
    return new Response(JSON.stringify({ error: "Invalid Firebase JWT" }), { status: 401 });
  }

  const { data: actorProfile } = await adminClient
    .from("profiles")
    .select("id, role, is_suspended")
    .eq("id", callerUid as string)
    .single();

  if (!actorProfile || actorProfile.is_suspended || !["admin", "moderator"].includes(actorProfile.role)) {
    return new Response(JSON.stringify({ error: "Forbidden: admin privileges required" }), { status: 403 });
  }

  const cmd = (await req.json()) as ModerationCommand;

  if (cmd.action === "suspend_user" || cmd.action === "unsuspend_user") {
    await adminClient
      .from("profiles")
      .update({ is_suspended: cmd.action === "suspend_user" })
      .eq("id", cmd.target_id);
  } else if (cmd.action === "remove_post") {
    await adminClient.from("posts").delete().eq("id", cmd.target_id);
  }

  if (cmd.report_id) {
    await adminClient
      .from("reports")
      .update({
        status: cmd.action === "dismiss_report" ? "dismissed" : "resolved",
        reviewed_by: callerUid as string,
      })
      .eq("id", cmd.report_id);
  }

  await adminClient.from("audit_logs").insert({
    actor_id: callerUid as string,
    action: cmd.action,
    entity_type: cmd.action.includes("user") ? "profile" : "post",
    entity_id: cmd.target_id,
    metadata: { reason: cmd.reason ?? "", report_id: cmd.report_id ?? null },
  });

  return new Response(JSON.stringify({ ok: true }), {
    headers: { "Content-Type": "application/json" },
  });
});
