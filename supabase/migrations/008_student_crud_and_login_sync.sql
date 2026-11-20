-- ============================================================================
-- AZRIL CLAS - Migration 008: Authoritative Student CRUD, Trigger Sync, &
--                             Whitespace-Resilient Student Login Lookup
-- Safe, idempotent, non-destructive migration (preserves all existing data,
-- creates NO duplicate tables, keeps RLS strictly ENABLED on all tables).
-- ============================================================================

-- 1. Upgrade handle_new_student_signup() so students created via /auth/v1/signup
--    populate all academic metadata (nim, class_id, semester, cohort_year,
--    student_badge_status, must_change_password, internal_auth_email) safely.
CREATE OR REPLACE FUNCTION public.handle_new_student_signup()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_prodi_id UUID;
    v_class_id TEXT;
    v_semester INTEGER;
    v_cohort INTEGER;
    v_badge TEXT;
    v_must_change BOOLEAN;
    v_nim TEXT;
BEGIN
    SELECT id INTO v_prodi_id
    FROM public.study_programs
    WHERE id::text = COALESCE(NEW.raw_user_meta_data->>'study_program_id', '')
       OR upper(code) = upper(COALESCE(NEW.raw_user_meta_data->>'study_program_code', NEW.raw_user_meta_data->>'study_program', ''))
    LIMIT 1;

    v_class_id := NULLIF(trim(COALESCE(NEW.raw_user_meta_data->>'class_id', '')), '');
    IF v_class_id IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM public.classes c WHERE c.id = v_class_id) THEN
            v_class_id := NULL;
        END IF;
    END IF;

    v_nim := NULLIF(
        regexp_replace(COALESCE(NEW.raw_user_meta_data->>'nim', ''), '[\s\u00A0\u200B-\u200D\uFEFF]+', '', 'g'),
        ''
    );

    v_semester := COALESCE(
        NULLIF(NEW.raw_user_meta_data->>'semester', '')::INTEGER,
        1
    );
    v_cohort := COALESCE(
        NULLIF(NEW.raw_user_meta_data->>'cohort_year', '')::INTEGER,
        2026
    );
    v_badge := lower(trim(COALESCE(NEW.raw_user_meta_data->>'student_badge_status', 'neutral')));
    IF v_badge NOT IN ('afk', 'neutral', 'lulus') THEN
        v_badge := 'neutral';
    END IF;
    v_must_change := COALESCE(
        NULLIF(NEW.raw_user_meta_data->>'must_change_password', '')::BOOLEAN,
        FALSE
    );

    INSERT INTO public.profiles (
        id,
        full_name,
        nim,
        email,
        internal_auth_email,
        username,
        study_program_id,
        class_id,
        semester,
        cohort_year,
        role,
        account_status,
        student_badge_status,
        must_change_password
    ) VALUES (
        NEW.id,
        COALESCE(NULLIF(trim(NEW.raw_user_meta_data->>'full_name'), ''), 'Mahasiswa'),
        v_nim,
        lower(trim(COALESCE(NULLIF(NEW.raw_user_meta_data->>'academic_email', ''), NEW.email))),
        lower(trim(NEW.email)),
        COALESCE(NULLIF(trim(NEW.raw_user_meta_data->>'username'), ''), v_nim),
        v_prodi_id,
        v_class_id,
        v_semester,
        v_cohort,
        'student',
        'active',
        v_badge,
        v_must_change
    )
    ON CONFLICT (id) DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_student_signup();

-- 2. Harden student login identifier lookup (strips hidden spaces, NBSP, zero-width chars)
--    Always matches the exact same public.profiles table read by Admin Dashboard.
CREATE OR REPLACE FUNCTION public.resolve_login_email(p_identifier TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
    v_status TEXT;
    v_role TEXT;
    v_clean TEXT := lower(trim(regexp_replace(COALESCE(p_identifier, ''), '[\u00A0\u200B-\u200D\uFEFF]+', '', 'g')));
    v_compact TEXT := regexp_replace(v_clean, '\s+', '', 'g');
BEGIN
    IF v_clean = '' THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(NULLIF(trim(internal_auth_email), ''), trim(email)),
           COALESCE(account_status, 'active'),
           role
    INTO v_email, v_status, v_role
    FROM public.profiles
    WHERE lower(regexp_replace(COALESCE(nim, ''), '[\s\u00A0\u200B-\u200D\uFEFF]+', '', 'g')) = v_compact
       OR lower(trim(COALESCE(username, ''))) = v_clean
       OR lower(trim(COALESCE(email, ''))) = v_clean
       OR lower(trim(COALESCE(internal_auth_email, ''))) = v_clean
    LIMIT 1;

    IF v_email IS NULL THEN
        RETURN NULL;
    END IF;

    IF v_role = 'admin' THEN
        RAISE EXCEPTION 'Akun Administrator harus masuk melalui halaman Login Admin.';
    END IF;

    IF lower(v_status) = 'inactive' THEN
        RAISE EXCEPTION 'Akun mahasiswa sedang dinonaktifkan oleh Admin.';
    END IF;

    RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_login_email(TEXT) TO anon, authenticated;

-- 3. Verify strict RLS policies remain enabled on profiles, classes, and study_programs
--    (Admin gets full SELECT/INSERT/UPDATE/DELETE; Students can ONLY read their own profile)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_programs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_manage_all_profiles" ON public.profiles;
CREATE POLICY "admin_manage_all_profiles"
ON public.profiles
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "student_read_own_profile" ON public.profiles;
CREATE POLICY "student_read_own_profile"
ON public.profiles
FOR SELECT
TO authenticated
USING (id = auth.uid());

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
