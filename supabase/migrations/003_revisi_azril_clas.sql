-- ============================================================================
-- AZRIL CLAS - Migration 003: Revisi Fitur Aplikasi Azril Clas
-- Safe, idempotent, non-destructive migration (preserves existing data).
-- Covers:
--   1. Admin-only student provisioning, NIM login, & Student Cap Status (AFK / Netral / Lulus)
--   2. Classes, Assignments with multi-class OR prodi targeting, 2 task files,
--      unlimited TEXT description/guide, and TIMESTAMPTZ deadline
--   3. Strict Supabase Row Level Security (RLS) policies
-- ============================================================================

-- 1. Classes table (idempotent)
CREATE TABLE IF NOT EXISTS public.classes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    study_program_id UUID REFERENCES public.study_programs(id) ON DELETE RESTRICT,
    study_program_code TEXT NOT NULL,
    semester INTEGER NOT NULL DEFAULT 1,
    academic_year TEXT NOT NULL DEFAULT '2026/2027',
    cohort_year INTEGER NOT NULL DEFAULT 2026,
    description TEXT NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Non-destructively extend profiles with class_id, semester, cohort_year,
--    account_status, and student_badge_status ('afk' | 'neutral' | 'lulus')
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS class_id TEXT REFERENCES public.classes(id) ON DELETE SET NULL;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS semester INTEGER NOT NULL DEFAULT 1;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS cohort_year INTEGER NOT NULL DEFAULT 2026;

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active';

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS student_badge_status TEXT NOT NULL DEFAULT 'neutral';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'profiles_student_badge_status_check'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT profiles_student_badge_status_check
            CHECK (student_badge_status IN ('afk', 'neutral', 'lulus'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_profiles_class_id ON public.profiles(class_id);
CREATE INDEX IF NOT EXISTS idx_profiles_badge_status ON public.profiles(student_badge_status);

-- 3. Update login resolver so students log in using NIM (while still supporting username/email for Admin)
CREATE OR REPLACE FUNCTION public.resolve_login_email(p_identifier TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
    v_clean TEXT := lower(trim(p_identifier));
BEGIN
    SELECT email INTO v_email
    FROM public.profiles
    WHERE lower(nim) = v_clean
       OR lower(username) = v_clean
       OR lower(email) = v_clean
    LIMIT 1;

    RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_login_email(TEXT) TO anon, authenticated;

-- 4. Assignments & Assignment Targets (multi-class OR study_program targeting)
CREATE TABLE IF NOT EXISTS public.assignments (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',           -- Unlimited length TEXT
    instructions TEXT NOT NULL DEFAULT '',          -- Unlimited length TEXT
    target_type TEXT NOT NULL DEFAULT 'class' CHECK (target_type IN ('class', 'study_program')),
    course_category TEXT NOT NULL DEFAULT 'Umum',
    file1_name TEXT,
    file1_url TEXT,
    file2_name TEXT,
    file2_url TEXT,
    deadline TIMESTAMPTZ NOT NULL,
    allowed_formats TEXT[] NOT NULL DEFAULT ARRAY['PDF', 'DOCX'],
    max_file_size_mb INTEGER NOT NULL DEFAULT 10,
    submission_method TEXT NOT NULL DEFAULT 'BOTH',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.assignment_targets (
    id BIGSERIAL PRIMARY KEY,
    assignment_id TEXT NOT NULL REFERENCES public.assignments(id) ON DELETE CASCADE,
    class_id TEXT REFERENCES public.classes(id) ON DELETE CASCADE,
    study_program_id UUID REFERENCES public.study_programs(id) ON DELETE CASCADE,
    study_program_code TEXT,
    CONSTRAINT assignment_targets_one_target_check CHECK (
        class_id IS NOT NULL OR study_program_id IS NOT NULL OR study_program_code IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_assignment_targets_assignment ON public.assignment_targets(assignment_id);
CREATE INDEX IF NOT EXISTS idx_assignment_targets_class ON public.assignment_targets(class_id);
CREATE INDEX IF NOT EXISTS idx_assignment_targets_prodi ON public.assignment_targets(study_program_id);

-- 5. Helper function to check Admin role safely inside RLS
CREATE OR REPLACE FUNCTION public.is_azril_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.profiles p
        WHERE p.id = auth.uid()
          AND p.role = 'admin'
    );
$$;

-- 6. Strict Row Level Security (RLS)
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_targets ENABLE ROW LEVEL SECURITY;

-- Profiles: Students can ONLY read their own profile and CANNOT self-edit academic fields or badge status.
-- Only Admins can insert, update, or delete student profiles & change AFK/Netral/Lulus status.
DROP POLICY IF EXISTS "Users can update own non-role profile fields" ON public.profiles;
DROP POLICY IF EXISTS "Admins can manage all profiles" ON public.profiles;
CREATE POLICY "Admins can manage all profiles"
ON public.profiles
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

-- Classes: Authenticated users can read; only Admins can write
DROP POLICY IF EXISTS "Authenticated users can read classes" ON public.classes;
CREATE POLICY "Authenticated users can read classes"
ON public.classes
FOR SELECT
TO authenticated
USING (true);

DROP POLICY IF EXISTS "Admins can manage classes" ON public.classes;
CREATE POLICY "Admins can manage classes"
ON public.classes
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

-- Assignments: Admins have full CRUD; Students ONLY see active assignments targeted to their class or study program
DROP POLICY IF EXISTS "Admins can manage assignments" ON public.assignments;
CREATE POLICY "Admins can manage assignments"
ON public.assignments
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "Students can read targeted active assignments" ON public.assignments;
CREATE POLICY "Students can read targeted active assignments"
ON public.assignments
FOR SELECT
TO authenticated
USING (
    is_active = TRUE
    AND EXISTS (
        SELECT 1
        FROM public.profiles me
        JOIN public.assignment_targets t ON t.assignment_id = assignments.id
        WHERE me.id = auth.uid()
          AND (
              (assignments.target_type = 'class' AND t.class_id IS NOT NULL AND t.class_id = me.class_id)
              OR
              (assignments.target_type = 'study_program' AND (
                  (t.study_program_id IS NOT NULL AND t.study_program_id = me.study_program_id)
                  OR (
                      t.study_program_code IS NOT NULL
                      AND EXISTS (
                          SELECT 1 FROM public.study_programs sp
                          WHERE sp.id = me.study_program_id
                            AND upper(sp.code) = upper(t.study_program_code)
                      )
                  )
              ))
          )
    )
);

-- Assignment Targets: Admins manage; students can read targets for assignments visible to them
DROP POLICY IF EXISTS "Admins can manage assignment targets" ON public.assignment_targets;
CREATE POLICY "Admins can manage assignment targets"
ON public.assignment_targets
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "Students can read matching assignment targets" ON public.assignment_targets;
CREATE POLICY "Students can read matching assignment targets"
ON public.assignment_targets
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.profiles me
        WHERE me.id = auth.uid()
          AND (
              (assignment_targets.class_id IS NOT NULL AND assignment_targets.class_id = me.class_id)
              OR (assignment_targets.study_program_id IS NOT NULL AND assignment_targets.study_program_id = me.study_program_id)
          )
    )
);

-- NOTE FOR PRODUCTION PASSWORD PROVISIONING / RESET:
-- Never store plaintext passwords in public.profiles or ship SUPABASE_SERVICE_ROLE_KEY in the client app.
-- Deploy a Supabase Edge Function (e.g., `admin-manage-student`) verified by the Admin's JWT
-- that calls `supabaseAdmin.auth.admin.createUser(...)` and `supabaseAdmin.auth.admin.updateUserById(id, { password })`.
