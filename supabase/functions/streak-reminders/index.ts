// Supabase Edge Function: streak-reminders
// Scheduled via Supabase Cron (08:00 WIB = 01:00 UTC, 12:00 WIB = 05:00 UTC, 17:00 WIB = 10:00 UTC).
// Invokes public.dispatch_due_streak_reminders(p_slot) so reminders run reliably server-side
// without requiring the student's app/browser to remain open.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

function resolveWibSlot(date: Date): "MORNING_08" | "NOON_12" | "EVENING_17" | null {
  const wibHour = (date.getUTCHours() + 7) % 24;
  if (wibHour >= 8 && wibHour < 12) return "MORNING_08";
  if (wibHour >= 12 && wibHour < 17) return "NOON_12";
  if (wibHour >= 17) return "EVENING_17";
  return null;
}

Deno.serve(async (req) => {
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) {
      return new Response(
        JSON.stringify({ error: "Missing server-side Supabase environment variables." }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
    const slot = body?.slot ?? resolveWibSlot(new Date());
    if (!slot) {
      return new Response(
        JSON.stringify({ ok: true, dispatched: 0, note: "Outside reminder window (WIB)." }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false },
    });

    const { data, error } = await adminClient.rpc("dispatch_due_streak_reminders", {
      p_slot: slot,
    });

    if (error) {
      return new Response(
        JSON.stringify({ ok: false, error: error.message }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ ok: true, slot, dispatched: data ?? 0, timezone: "Asia/Jakarta" }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ ok: false, error: String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
