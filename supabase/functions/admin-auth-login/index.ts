// Supabase Edge Function: admin-auth-login
// Secure backend username-to-Supabase-Auth login for Administrators + server-side
// primary admin bootstrap. NEVER embeds plaintext passwords in source code.
//
// Required Supabase Server Secrets:
//   - SUPABASE_URL
//   - SUPABASE_ANON_KEY
//   - SUPABASE_SERVICE_ROLE_KEY
// For one-time primary admin bootstrap (action: "bootstrap_primary_admin"):
//   - ADMIN_BOOTSTRAP_SECRET
//   - PRIMARY_ADMIN_USERNAME (e.g., "Azril12v")
//   - PRIMARY_ADMIN_PASSWORD (stored ONLY in Supabase Server Secrets)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-bootstrap-secret",
};

function internalAdminAuthEmail(username: string): string {
  const safe = username.trim().toLowerCase().replace(/[^a-z0-9._-]/g, "");
  return `admin_${safe}@auth.azrilclas.internal`;
}

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed." }, 405);
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      return jsonResponse({ error: "Server environment variables are not configured." }, 500);
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);
    const body = await req.json();
    const action = String(body?.action ?? "login").trim();

    // =========================================================================
    // ACTION 1: Idempotent server-side bootstrap of Primary Admin account
    // Credentials are read EXCLUSIVELY from Supabase Server Secrets.
    // =========================================================================
    if (action === "bootstrap_primary_admin") {
      const expectedBootstrapSecret = Deno.env.get("ADMIN_BOOTSTRAP_SECRET");
      const providedSecret = req.headers.get("x-bootstrap-secret") ?? "";
      if (!expectedBootstrapSecret || providedSecret !== expectedBootstrapSecret) {
        return jsonResponse({ error: "Unauthorized bootstrap request." }, 401);
      }

      const primaryUsername = (Deno.env.get("PRIMARY_ADMIN_USERNAME") ?? "Azril12v").trim();
      const primaryPassword = Deno.env.get("PRIMARY_ADMIN_PASSWORD") ?? "";
      if (primaryUsername.length < 4 || primaryPassword.length < 8) {
        return jsonResponse(
          { error: "PRIMARY_ADMIN_USERNAME atau PRIMARY_ADMIN_PASSWORD belum dikonfigurasi di Supabase Secrets." },
          400
        );
      }

      const authEmail = internalAdminAuthEmail(primaryUsername);

      // Check existing profile for this admin username
      const { data: existingProfile } = await adminClient
        .from("profiles")
        .select("id, username, role")
        .ilike("username", primaryUsername)
        .maybeSingle();

      let userId: string;
      if (existingProfile?.id) {
        userId = existingProfile.id;
        const { error: updateAuthErr } = await adminClient.auth.admin.updateUserById(userId, {
          email: authEmail,
          password: primaryPassword,
          email_confirm: true,
          user_metadata: {
            full_name: "Administrator Utama Azril Clas",
            username: primaryUsername,
            role: "admin",
          },
        });
        if (updateAuthErr) throw updateAuthErr;
      } else {
        const { data: created, error: createErr } = await adminClient.auth.admin.createUser({
          email: authEmail,
          password: primaryPassword,
          email_confirm: true,
          user_metadata: {
            full_name: "Administrator Utama Azril Clas",
            username: primaryUsername,
            role: "admin",
          },
        });
        if (createErr || !created?.user) {
          throw createErr ?? new Error("Gagal membuat akun Administrator di Supabase Auth.");
        }
        userId = created.user.id;
      }

      // Enforce role = 'admin' in public.profiles (overriding default trigger role 'student')
      const { error: upsertErr } = await adminClient.from("profiles").upsert(
        {
          id: userId,
          full_name: "Administrator Utama Azril Clas",
          nim: null,
          email: "admin@azrilclas.ac.id",
          internal_auth_email: authEmail,
          username: primaryUsername,
          study_program_id: null,
          role: "admin",
          account_status: "active",
          must_change_password: false,
          updated_at: new Date().toISOString(),
        },
        { onConflict: "id" }
      );
      if (upsertErr) throw upsertErr;

      return jsonResponse({
        ok: true,
        adminId: userId,
        username: primaryUsername,
        role: "admin",
      });
    }

    // =========================================================================
    // ACTION 2: Secure Admin Login (Username -> Internal Auth Email -> Supabase Auth)
    // =========================================================================
    const username = String(body?.username ?? "").trim();
    const password = String(body?.password ?? "");

    if (!username || !password) {
      return jsonResponse({ error: "Username dan password administrator wajib diisi." }, 400);
    }

    // Look up ONLY matching username in profiles using server-side service client
    const { data: profileRow } = await adminClient
      .from("profiles")
      .select("id, full_name, email, internal_auth_email, username, role, account_status, created_at")
      .ilike("username", username)
      .maybeSingle();

    if (!profileRow || profileRow.role !== "admin") {
      await adminClient.from("admin_auth_audit_logs").insert({
        attempted_username: username,
        actor_user_id: profileRow?.id ?? null,
        event_type: profileRow ? "UNAUTHORIZED_ADMIN_ACCESS" : "ADMIN_LOGIN_FAILED",
        detail: profileRow
          ? "Akun non-admin mencoba login melalui portal Administrator."
          : "Username administrator tidak ditemukan.",
      });
      return jsonResponse(
        { error: "Username atau password administrator tidak valid." },
        401
      );
    }

    if ((profileRow.account_status ?? "active") !== "active") {
      return jsonResponse({ error: "Akun administrator sedang dinonaktifkan." }, 403);
    }

    const authEmail = (profileRow.internal_auth_email || profileRow.email || "").trim().toLowerCase();
    if (!authEmail) {
      return jsonResponse({ error: "Konfigurasi autentikasi administrator belum lengkap." }, 500);
    }

    // Authenticate against Supabase Auth (GoTrue)
    const authClient = createClient(supabaseUrl, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: signInData, error: signInErr } = await authClient.auth.signInWithPassword({
      email: authEmail,
      password,
    });

    if (signInErr || !signInData?.session || !signInData?.user) {
      await adminClient.from("admin_auth_audit_logs").insert({
        attempted_username: username,
        actor_user_id: profileRow.id,
        event_type: "ADMIN_LOGIN_FAILED",
        detail: "Password administrator salah.",
      });
      return jsonResponse(
        { error: "Username atau password administrator tidak valid." },
        401
      );
    }

    // Double-check authenticated user ID matches verified admin profile ID
    if (signInData.user.id !== profileRow.id || profileRow.role !== "admin") {
      return jsonResponse({ error: "Verifikasi role administrator gagal." }, 403);
    }

    await adminClient.from("admin_auth_audit_logs").insert({
      attempted_username: username,
      actor_user_id: profileRow.id,
      event_type: "ADMIN_LOGIN_SUCCESS",
      detail: "Login administrator berhasil melalui Supabase Auth.",
    });

    return jsonResponse({
      access_token: signInData.session.access_token,
      refresh_token: signInData.session.refresh_token,
      expires_at: signInData.session.expires_at,
      expires_in: signInData.session.expires_in,
      profile: {
        id: profileRow.id,
        full_name: profileRow.full_name,
        username: profileRow.username,
        email: profileRow.email,
        role: "admin",
        account_status: profileRow.account_status ?? "active",
        created_at: profileRow.created_at,
      },
    });
  } catch (err) {
    return jsonResponse(
      { error: err instanceof Error ? err.message : "Terjadi kesalahan autentikasi server." },
      500
    );
  }
});
