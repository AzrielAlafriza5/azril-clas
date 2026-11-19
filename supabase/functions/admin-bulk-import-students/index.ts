// Supabase Edge Function: admin-bulk-import-students
// Secure server-side bulk account provisioning, password reset, and NIM sync.
// Uses SUPABASE_SERVICE_ROLE_KEY strictly on the server (NEVER exposed to client).
//
// Supported actions (POST JSON):
//   1. { action: "bulk_import", fileName, semesterKey, importHistoricalAttendance, rows: [...] }
//   2. { action: "reset_password", studentId, newPassword, mustChangePassword: true }
//   3. { action: "update_nim", studentId, newNim }

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface BulkRowInput {
  sheetName: string;
  rowNumber: number;
  nim: string;
  fullName: string;
  studyProgramCode: string;
  className: string;
  semester: number;
  colorFlag: "NONE" | "RED_AFK" | "BLACK_SKIP" | "OTHER_IGNORED";
  weeklyAttendance?: Record<string, string>;
}

const ALLOWED_PRODI = new Set(["PJKR", "FARMASI", "HES"]);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function internalAuthEmailForNim(nim: string): string {
  const safeNim = nim.trim().toLowerCase().replace(/[^a-z0-9._-]/g, "");
  return `nim_${safeNim}@auth.azrilclas.internal`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      return new Response(
        JSON.stringify({ error: "Server environment variables are not configured." }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: "Missing Authorization header." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Verify caller JWT & Admin role
    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userData, error: userErr } = await callerClient.auth.getUser();
    if (userErr || !userData?.user) {
      return new Response(
        JSON.stringify({ error: "Invalid session token." }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);
    const { data: adminProfile } = await adminClient
      .from("profiles")
      .select("id, role")
      .eq("id", userData.user.id)
      .single();

    if (!adminProfile || adminProfile.role !== "admin") {
      return new Response(
        JSON.stringify({ error: "Akses ditolak: hanya Admin yang diizinkan." }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const body = await req.json();
    const action = body?.action ?? "bulk_import";

    if (action === "reset_password") {
      const studentId = String(body.studentId ?? "").trim();
      const newPassword = String(body.newPassword ?? "").trim();
      if (!studentId || newPassword.length < 6) {
        return new Response(
          JSON.stringify({ error: "ID mahasiswa atau kata sandi baru tidak valid (minimal 6 karakter)." }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { error: pwErr } = await adminClient.auth.admin.updateUserById(studentId, {
        password: newPassword,
      });
      if (pwErr) throw pwErr;

      await adminClient
        .from("profiles")
        .update({ must_change_password: body.mustChangePassword !== false, updated_at: new Date().toISOString() })
        .eq("id", studentId);

      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (action === "update_nim") {
      const studentId = String(body.studentId ?? "").trim();
      const newNim = String(body.newNim ?? "").trim();
      if (!studentId || newNim.length < 5 || newNim.includes(" ")) {
        return new Response(
          JSON.stringify({ error: "Format NIM baru tidak valid." }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const { data: conflict } = await adminClient
        .from("profiles")
        .select("id")
        .ilike("nim", newNim)
        .neq("id", studentId)
        .maybeSingle();

      if (conflict) {
        return new Response(
          JSON.stringify({ error: `NIM ${newNim} sudah digunakan mahasiswa lain.` }),
          { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      const newInternalEmail = internalAuthEmailForNim(newNim);
      const { error: authErr } = await adminClient.auth.admin.updateUserById(studentId, {
        email: newInternalEmail,
        email_confirm: true,
        user_metadata: { nim: newNim, username: newNim },
      });
      if (authErr) throw authErr;

      await adminClient
        .from("profiles")
        .update({
          nim: newNim,
          username: newNim,
          internal_auth_email: newInternalEmail,
          updated_at: new Date().toISOString(),
        })
        .eq("id", studentId);

      await adminClient
        .from("historical_attendance_records")
        .update({ student_nim: newNim })
        .eq("student_id", studentId);

      return new Response(JSON.stringify({ ok: true, nim: newNim }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Default action: bulk_import
    const fileName = String(body.fileName ?? "import.xlsx");
    const semesterKey = String(body.semesterKey ?? "2026/2027-Ganjil");
    const importHistorical = Boolean(body.importHistoricalAttendance);
    const inputRows: BulkRowInput[] = Array.isArray(body.rows) ? body.rows : [];

    // Fetch existing study programs & existing NIMs
    const { data: prodis } = await adminClient.from("study_programs").select("id, code");
    const prodiMap = new Map<string, string>();
    (prodis ?? []).forEach((p) => prodiMap.set(String(p.code).toUpperCase(), String(p.id)));

    const { data: existingProfiles } = await adminClient.from("profiles").select("id, nim");
    const existingNims = new Set(
      (existingProfiles ?? [])
        .map((p) => String(p.nim ?? "").trim().toLowerCase())
        .filter((s) => s.length > 0)
    );

    const seenInBatch = new Set<string>();
    let importedNormal = 0;
    let importedAfk = 0;
    let skippedBlack = 0;
    let skippedExisting = 0;
    let invalidOrDuplicate = 0;
    const errors: Array<{ sheet: string; row: number; reason: string }> = [];
    const createdStudents: Array<{ id: string; nim: string; fullName: string; badgeStatus: string }> = [];

    // Create audit batch record first
    const { data: batchRow } = await adminClient
      .from("student_import_batches")
      .insert({
        admin_id: adminProfile.id,
        file_name: fileName,
        sheet_names: [...new Set(inputRows.map((r) => r.sheetName))],
        imported_historical_attendance: importHistorical,
      })
      .select("id")
      .single();
    const batchId = batchRow?.id ?? null;

    // Ensure classes exist
    const classIdCache = new Map<string, string>();
    for (const row of inputRows) {
      // Rule: NEVER import black-highlighted rows
      if (row.colorFlag === "BLACK_SKIP") {
        skippedBlack++;
        continue;
      }

      const cleanNim = String(row.nim ?? "").trim();
      const cleanName = String(row.fullName ?? "").trim();
      const prodiCode = String(row.studyProgramCode ?? "").trim().toUpperCase();
      const className = String(row.className ?? row.sheetName ?? "").trim();

      if (!cleanNim || cleanNim.length < 5 || cleanNim.includes(" ")) {
        invalidOrDuplicate++;
        errors.push({ sheet: row.sheetName, row: row.rowNumber, reason: "NIM kosong atau format tidak valid" });
        continue;
      }
      if (!cleanName || cleanName.length < 2) {
        invalidOrDuplicate++;
        errors.push({ sheet: row.sheetName, row: row.rowNumber, reason: "Nama mahasiswa kosong" });
        continue;
      }
      if (!ALLOWED_PRODI.has(prodiCode)) {
        invalidOrDuplicate++;
        errors.push({ sheet: row.sheetName, row: row.rowNumber, reason: `Program studi '${prodiCode}' tidak dikenali` });
        continue;
      }
      const nimLower = cleanNim.toLowerCase();
      if (existingNims.has(nimLower)) {
        skippedExisting++;
        continue;
      }
      if (seenInBatch.has(nimLower)) {
        invalidOrDuplicate++;
        errors.push({ sheet: row.sheetName, row: row.rowNumber, reason: `NIM '${cleanNim}' duplikat dalam file` });
        continue;
      }
      seenInBatch.add(nimLower);

      const prodiId = prodiMap.get(prodiCode) ?? null;
      let classId = classIdCache.get(className.toLowerCase()) ?? null;
      if (!classId && className) {
        const computedClassId = `cls-${className.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
        await adminClient.from("classes").upsert(
          {
            id: computedClassId,
            name: className,
            study_program_id: prodiId,
            study_program_code: prodiCode,
            semester: row.semester || 3,
            academic_year: "2026/2027",
            cohort_year: 2025,
            is_active: true,
          },
          { onConflict: "id" }
        );
        classId = computedClassId;
        classIdCache.set(className.toLowerCase(), classId);
      }

      const internalEmail = internalAuthEmailForNim(cleanNim);
      const badgeStatus = row.colorFlag === "RED_AFK" ? "afk" : "neutral";

      // Create Supabase Auth account: Username = NIM, Initial Password = NIM
      const { data: createdAuth, error: createErr } = await adminClient.auth.admin.createUser({
        email: internalEmail,
        password: cleanNim,
        email_confirm: true,
        user_metadata: {
          full_name: cleanName,
          nim: cleanNim,
          username: cleanNim,
          study_program: prodiCode,
          class_name: className,
          role: "student",
        },
      });

      if (createErr || !createdAuth?.user) {
        invalidOrDuplicate++;
        errors.push({
          sheet: row.sheetName,
          row: row.rowNumber,
          reason: createErr?.message ?? "Gagal membuat akun auth",
        });
        continue;
      }

      const newUserId = createdAuth.user.id;
      const { error: profileErr } = await adminClient.from("profiles").upsert({
        id: newUserId,
        full_name: cleanName,
        nim: cleanNim,
        username: cleanNim,
        email: `${cleanNim}@mahasiswa.azrilclas.ac.id`,
        internal_auth_email: internalEmail,
        study_program_id: prodiId,
        class_id: classId,
        semester: row.semester || 3,
        cohort_year: 2025,
        role: "student",
        account_status: "active",
        student_badge_status: badgeStatus,
        must_change_password: true,
        import_batch_id: batchId,
        imported_from_sheet: row.sheetName,
      });

      if (profileErr) {
        await adminClient.auth.admin.deleteUser(newUserId);
        invalidOrDuplicate++;
        errors.push({ sheet: row.sheetName, row: row.rowNumber, reason: profileErr.message });
        continue;
      }

      existingNims.add(nimLower);
      if (badgeStatus === "afk") importedAfk++;
      else importedNormal++;
      createdStudents.push({ id: newUserId, nim: cleanNim, fullName: cleanName, badgeStatus });

      // Optional historical attendance import (Weeks 1..8, exact raw status, xp_calculated = false)
      if (importHistorical && row.weeklyAttendance) {
        const histRows = Object.entries(row.weeklyAttendance)
          .map(([wkStr, rawStatus]) => ({
            student_id: newUserId,
            student_nim: cleanNim,
            student_name: cleanName,
            class_id: classId,
            class_name: className,
            study_program_code: prodiCode,
            week_number: Number(wkStr),
            week_label: `Minggu ke-${wkStr}`,
            raw_status: String(rawStatus).trim(),
            source_sheet: row.sheetName,
            semester_key: semesterKey,
            xp_calculated: false,
            import_batch_id: batchId,
          }))
          .filter((h) => h.week_number >= 1 && h.raw_status.length > 0);

        if (histRows.length > 0) {
          await adminClient
            .from("historical_attendance_records")
            .upsert(histRows, { onConflict: "student_id,semester_key,week_number" });
        }
      }
    }

    if (batchId) {
      await adminClient
        .from("student_import_batches")
        .update({
          imported_normal_count: importedNormal,
          imported_afk_count: importedAfk,
          skipped_black_count: skippedBlack,
          skipped_existing_count: skippedExisting,
          invalid_or_duplicate_count: invalidOrDuplicate,
        })
        .eq("id", batchId);
    }

    return new Response(
      JSON.stringify({
        ok: true,
        batchId,
        importedNormal,
        importedAfk,
        skippedBlack,
        skippedExisting,
        invalidOrDuplicate,
        createdStudents,
        errors,
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ error: err instanceof Error ? err.message : String(err) }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
