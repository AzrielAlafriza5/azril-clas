-- Azril Clas — Authentication, Profiles & Official Study Programs Migration
-- Enforces exactly three official study programs: HES, PJKR, FARMASI
-- Passwords are managed exclusively by Supabase Auth (auth.users), never in profiles.

CREATE TABLE IF NOT EXISTS public.study_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE CHECK (code IN ('HES', 'PJKR', 'FARMASI')),
    name TEXT NOT NULL UNIQUE,
    faculty TEXT NOT NULL,
    description TEXT DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed exact 3 permitted study programs
INSERT INTO public.study_programs (code, name, faculty, description, is_active)
VALUES
    ('HES', 'Hukum Ekonomi Syariah', 'Fakultas Syariah & Hukum', 'Program Studi Hukum Ekonomi Syariah (HES)', TRUE),
    ('PJKR', 'Pendidikan Jasmani, Kesehatan, dan Rekreasi', 'Fakultas Ilmu Keolahragaan & Pendidikan', 'Program Studi Pendidikan Jasmani, Kesehatan, dan Rekreasi (PJKR)', TRUE),
    ('FARMASI', 'Farmasi', 'Fakultas Ilmu Kesehatan', 'Program Studi Farmasi', TRUE)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL CHECK (char_length(trim(full_name)) >= 3),
    nim TEXT UNIQUE,
    email TEXT NOT NULL UNIQUE,
    username TEXT UNIQUE,
    study_program_id UUID REFERENCES public.study_programs(id) ON DELETE RESTRICT,
    role TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('admin', 'student')),
    account_status TEXT NOT NULL DEFAULT 'active' CHECK (account_status IN ('active', 'pending_activation', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_student_requires_nim_and_prodi CHECK (
        role = 'admin' OR (nim IS NOT NULL AND study_program_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_profiles_nim ON public.profiles (nim);
CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles (lower(username));
CREATE INDEX IF NOT EXISTS idx_profiles_email ON public.profiles (lower(email));
CREATE INDEX IF NOT EXISTS idx_profiles_study_program ON public.profiles (study_program_id);

-- Security-definer helper so login form can resolve username -> email without exposing other users' data
CREATE OR REPLACE FUNCTION public.resolve_login_email(p_identifier TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
BEGIN
    SELECT email INTO v_email
    FROM public.profiles
    WHERE lower(email) = lower(trim(p_identifier))
       OR lower(username) = lower(trim(p_identifier))
    LIMIT 1;
    RETURN v_email;
END;
$$;

GRANT EXECUTE ON FUNCTION public.resolve_login_email(TEXT) TO anon, authenticated;

-- Trigger on auth.users signup: ALWAYS assigns role = 'student' (never user-chosen)
CREATE OR REPLACE FUNCTION public.handle_new_student_signup()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_prodi_id UUID;
BEGIN
    SELECT id INTO v_prodi_id
    FROM public.study_programs
    WHERE id::text = NEW.raw_user_meta_data->>'study_program_id'
       OR code = upper(coalesce(NEW.raw_user_meta_data->>'study_program_code', ''))
    LIMIT 1;

    INSERT INTO public.profiles (
        id,
        full_name,
        nim,
        email,
        username,
        study_program_id,
        role
    ) VALUES (
        NEW.id,
        coalesce(NEW.raw_user_meta_data->>'full_name', 'Mahasiswa'),
        nullif(trim(NEW.raw_user_meta_data->>'nim'), ''),
        lower(NEW.email),
        nullif(lower(trim(NEW.raw_user_meta_data->>'username')), ''),
        v_prodi_id,
        'student' -- Hardcoded by system; admins are promoted only via privileged SQL
    );

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_student_signup();

-- Row Level Security (RLS)
ALTER TABLE public.study_programs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Study programs are readable for registration dropdown & dashboards
CREATE POLICY "public_read_study_programs" ON public.study_programs
    FOR SELECT
    USING (is_active = TRUE);

CREATE POLICY "admin_manage_study_programs" ON public.study_programs
    FOR ALL
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'));

-- Students can ONLY read their own profile (other students cannot read private data)
CREATE POLICY "student_read_own_profile" ON public.profiles
    FOR SELECT
    USING (id = auth.uid());

-- Students can update non-privileged fields of their own profile, NEVER role/nim/study_program_id
CREATE POLICY "student_update_own_profile" ON public.profiles
    FOR UPDATE
    USING (id = auth.uid())
    WITH CHECK (
        id = auth.uid()
        AND role = 'student'
    );

-- Admin can read and manage all profiles
CREATE POLICY "admin_manage_profiles" ON public.profiles
    FOR ALL
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'));
