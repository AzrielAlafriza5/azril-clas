-- ============================================================================
-- AZRIL CLAS - Migration 005: Bulk Excel Student Import, First-Login Password
--                             Change Enforcement, & Historical Attendance
-- Safe, idempotent, non-destructive migration (preserves all existing data).
-- ============================================================================

-- 1. Extend profiles with forced first-login password change & internal auth mapping
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS internal_auth_email TEXT;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS import_batch_id UUID;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS imported_from_sheet TEXT;

-- Ensure case-insensitive unique NIM constraint (skipping null/blank values for admins)
CREATE UNIQUE INDEX IF NOT EXISTS uq_profiles_nim_lower
    ON public.profiles (lower(trim(nim)))
    WHERE nim IS NOT NULL AND trim(nim) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_profiles_internal_auth_email_lower
    ON public.profiles (lower(trim(internal_auth_email)))
    WHERE internal_auth_email IS NOT NULL AND trim(internal_auth_email) <> '';

-- Ensure classes have unique name per study program to prevent duplicates on repeated imports
CREATE UNIQUE INDEX IF NOT EXISTS uq_classes_name_lower
    ON public.classes (lower(trim(name)));

-- 2. Bulk Excel Import Batch Audit Table
CREATE TABLE IF NOT EXISTS public.student_import_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    file_name TEXT NOT NULL,
    sheet_names TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    imported_normal_count INTEGER NOT NULL DEFAULT 0,
    imported_afk_count INTEGER NOT NULL DEFAULT 0,
    skipped_black_count INTEGER NOT NULL DEFAULT 0,
    skipped_existing_count INTEGER NOT NULL DEFAULT 0,
    invalid_or_duplicate_count INTEGER NOT NULL DEFAULT 0,
    imported_historical_attendance BOOLEAN NOT NULL DEFAULT FALSE,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Historical Attendance Table (Excel Weeks 1..8)
-- Preserves exact raw statuses (HADIR, IZIN, ALPHA, TIDAK HADIR, DISPEN, KIP, etc.)
-- Does NOT automatically award XP; xp_calculated stays FALSE until explicit Admin confirmation.
CREATE TABLE IF NOT EXISTS public.historical_attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    student_nim TEXT NOT NULL,
    student_name TEXT NOT NULL,
    class_id TEXT REFERENCES public.classes(id) ON DELETE SET NULL,
    class_name TEXT NOT NULL,
    study_program_code TEXT NOT NULL,
    week_number INTEGER NOT NULL CHECK (week_number >= 1 AND week_number <= 32),
    week_label TEXT NOT NULL,
    raw_status TEXT NOT NULL,
    source_sheet TEXT NOT NULL,
    semester_key TEXT NOT NULL DEFAULT '2026/2027-Ganjil',
    xp_calculated BOOLEAN NOT NULL DEFAULT FALSE,
    import_batch_id UUID REFERENCES public.student_import_batches(id) ON DELETE SET NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_historical_attendance_student_sem_week UNIQUE (student_id, semester_key, week_number)
);

CREATE INDEX IF NOT EXISTS idx_hist_att_student ON public.historical_attendance_records(student_id);
CREATE INDEX IF NOT EXISTS idx_hist_att_class ON public.historical_attendance_records(class_id);
CREATE INDEX IF NOT EXISTS idx_hist_att_semester ON public.historical_attendance_records(semester_key);

-- 4. Update NIM Login Resolver to support internal_auth_email & block inactive accounts
CREATE OR REPLACE FUNCTION public.resolve_login_email(p_identifier TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
    v_status TEXT;
    v_clean TEXT := lower(trim(p_identifier));
BEGIN
    SELECT COALESCE(NULLIF(trim(internal_auth_email), ''), email),
           COALESCE(account_status, 'active')
    INTO v_email, v_status
    FROM public.profiles
    WHERE lower(trim(nim)) = v_clean
       OR lower(trim(username)) = v_clean
       OR lower(trim(email)) = v_clean
       OR lower(trim(internal_auth_email)) = v_clean
    LIMIT 1;

    IF v_email IS NULL THEN
        RETURN NULL;
    END IF;

    IF lower(v_status) = 'inactive' THEN
        RAISE EXCEPTION 'Akun mahasiswa sedang dinonaktifkan oleh Admin.';
    END IF;

    RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_login_email(TEXT) TO anon, authenticated;

-- 5. Student Self-Service Completion of Mandatory First-Login Password Change
-- Called right after Supabase Auth `updateUser({ password })` succeeds.
CREATE OR REPLACE FUNCTION public.complete_first_login_password_change()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    UPDATE public.profiles
    SET must_change_password = FALSE,
        updated_at = NOW()
    WHERE id = auth.uid();

    RETURN FOUND;
END;
$$;

REVOKE ALL ON FUNCTION public.complete_first_login_password_change() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.complete_first_login_password_change() TO authenticated;

-- 6. Admin Atomic NIM Update (keeps profiles.nim, username, & historical_attendance_records consistent)
CREATE OR REPLACE FUNCTION public.admin_update_student_nim(
    p_student_id UUID,
    p_new_nim TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_nim TEXT := trim(p_new_nim);
BEGIN
    IF NOT public.is_azril_admin() THEN
        RAISE EXCEPTION 'Hanya Admin yang dapat mengubah NIM mahasiswa.';
    END IF;

    IF length(v_clean_nim) < 5 OR position(' ' in v_clean_nim) > 0 THEN
        RAISE EXCEPTION 'Format NIM tidak valid.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id <> p_student_id
          AND lower(trim(nim)) = lower(v_clean_nim)
    ) THEN
        RAISE EXCEPTION 'NIM % sudah digunakan oleh mahasiswa lain.', v_clean_nim;
    END IF;

    UPDATE public.profiles
    SET nim = v_clean_nim,
        username = v_clean_nim,
        updated_at = NOW()
    WHERE id = p_student_id;

    UPDATE public.historical_attendance_records
    SET student_nim = v_clean_nim
    WHERE student_id = p_student_id;

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_update_student_nim(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_update_student_nim(UUID, TEXT) TO authenticated;

-- 7. Strict Row Level Security (RLS)
ALTER TABLE public.student_import_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.historical_attendance_records ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_manage_import_batches" ON public.student_import_batches;
CREATE POLICY "admin_manage_import_batches"
ON public.student_import_batches
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "student_read_own_historical_attendance" ON public.historical_attendance_records;
CREATE POLICY "student_read_own_historical_attendance"
ON public.historical_attendance_records
FOR SELECT
TO authenticated
USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_historical_attendance" ON public.historical_attendance_records;
CREATE POLICY "admin_manage_historical_attendance"
ON public.historical_attendance_records
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());
