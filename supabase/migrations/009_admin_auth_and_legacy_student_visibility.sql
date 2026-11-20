-- =============================================================================
-- Azril Clas — Migration 009: Admin Auth Alignment & Legacy Student Visibility
-- =============================================================================
-- Tujuan (100% non-destruktif, idempoten, tanpa tabel duplikat, RLS tetap aktif):
-- 1. Menghilangkan potensi error rekursi RLS 42P17 pada tabel `public.profiles`
--    (jika policy lama `admin_manage_profiles` dari migrasi 001 masih aktif).
-- 2. Memperkuat RPC `resolve_admin_login_email` & `verify_admin_session` agar
--    Admin dapat login via Username (`Azril12v`) maupun Email Supabase Auth.
-- 3. Mengisi ulang (backfill) secara aman mahasiswa lama yang sudah ada di
--    `auth.users` (sehingga bisa login NIM) namun belum memiliki baris di
--    `public.profiles` (sehingga tidak tampil di Dashboard Admin).
--    Tidak menghapus data lama, tidak mengubah password, tidak menduplikasi data.
-- =============================================================================

-- 1. Pastikan kolom pendukung sudah ada di public.profiles (aman jika sudah ada)
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS internal_auth_email TEXT,
  ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'active',
  ADD COLUMN IF NOT EXISTS student_badge_status TEXT NOT NULL DEFAULT 'neutral',
  ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS class_id TEXT,
  ADD COLUMN IF NOT EXISTS semester INTEGER DEFAULT 1,
  ADD COLUMN IF NOT EXISTS cohort_year INTEGER DEFAULT 2025,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2. Pastikan RLS tetap AKTIF pada public.profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 3. Helper non-rekursif (SECURITY DEFINER) untuk mengecek hak akses Admin
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
      AND lower(trim(coalesce(p.role, ''))) = 'admin'
      AND lower(trim(coalesce(p.account_status, 'active'))) = 'active'
  );
$$;

REVOKE ALL ON FUNCTION public.is_azril_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_azril_admin() TO authenticated, service_role;

-- 4. Ganti policy lama yang berpotensi rekursif (42P17) dengan policy non-rekursif
DROP POLICY IF EXISTS "admin_manage_profiles" ON public.profiles;
DROP POLICY IF EXISTS "profiles_admin_all" ON public.profiles;
DROP POLICY IF EXISTS "profiles_select_own" ON public.profiles;
DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;

CREATE POLICY "profiles_admin_all"
  ON public.profiles
  FOR ALL
  TO authenticated
  USING (public.is_azril_admin())
  WITH CHECK (public.is_azril_admin());

CREATE POLICY "profiles_select_own"
  ON public.profiles
  FOR SELECT
  TO authenticated
  USING (id = auth.uid());

CREATE POLICY "profiles_update_own"
  ON public.profiles
  FOR UPDATE
  TO authenticated
  USING (id = auth.uid())
  WITH CHECK (
    id = auth.uid()
    AND lower(trim(coalesce(role, 'student'))) = 'student'
  );

-- 5. RPC verifikasi sesi Admin Supabase Auth
CREATE OR REPLACE FUNCTION public.verify_admin_session()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.is_azril_admin();
$$;

REVOKE ALL ON FUNCTION public.verify_admin_session() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.verify_admin_session() TO authenticated, service_role;

-- 6. RPC resolusi email login Admin (mendukung Username `Azril12v`, email publik, maupun internal_auth_email)
CREATE OR REPLACE FUNCTION public.resolve_admin_login_email(p_username TEXT)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_clean TEXT := lower(trim(coalesce(p_username, '')));
  v_email TEXT;
BEGIN
  IF v_clean = '' THEN
    RETURN NULL;
  END IF;

  SELECT coalesce(
    nullif(trim(u.email), ''),
    nullif(trim(p.internal_auth_email), ''),
    nullif(trim(p.email), '')
  )
  INTO v_email
  FROM public.profiles p
  LEFT JOIN auth.users u ON u.id = p.id
  WHERE lower(trim(coalesce(p.role, ''))) = 'admin'
    AND lower(trim(coalesce(p.account_status, 'active'))) = 'active'
    AND (
      lower(trim(coalesce(p.username, ''))) = v_clean
      OR lower(trim(coalesce(p.email, ''))) = v_clean
      OR lower(trim(coalesce(p.internal_auth_email, ''))) = v_clean
      OR lower(trim(coalesce(u.email, ''))) = v_clean
    )
  LIMIT 1;

  RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_admin_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_admin_login_email(TEXT) TO anon, authenticated, service_role;

-- 7. Backfill NON-DESTRUKTIF mahasiswa lama yang sudah ada di `auth.users`
--    tetapi belum memiliki baris di `public.profiles` (agar muncul di Dashboard Admin).
--    Baris yang sudah ada di `public.profiles` TIDAK disentuh sama sekali.
INSERT INTO public.profiles (
  id,
  full_name,
  nim,
  username,
  email,
  internal_auth_email,
  study_program_id,
  semester,
  cohort_year,
  role,
  account_status,
  student_badge_status,
  must_change_password,
  created_at,
  updated_at
)
SELECT
  u.id,
  coalesce(
    nullif(trim(u.raw_user_meta_data ->> 'full_name'), ''),
    nullif(trim(u.raw_user_meta_data ->> 'name'), ''),
    nullif(trim(u.raw_user_meta_data ->> 'nim'), ''),
    split_part(coalesce(u.email, 'mahasiswa'), '@', 1)
  ) AS full_name,
  coalesce(
    nullif(regexp_replace(coalesce(u.raw_user_meta_data ->> 'nim', ''), '\s+', '', 'g'), ''),
    nullif(regexp_replace(coalesce(u.raw_user_meta_data ->> 'username', ''), '\s+', '', 'g'), ''),
    regexp_replace(split_part(coalesce(u.email, ''), '@', 1), '^nim_', '', 'i')
  ) AS nim,
  coalesce(
    nullif(trim(u.raw_user_meta_data ->> 'username'), ''),
    nullif(trim(u.raw_user_meta_data ->> 'nim'), ''),
    regexp_replace(split_part(coalesce(u.email, ''), '@', 1), '^nim_', '', 'i')
  ) AS username,
  coalesce(
    nullif(lower(trim(u.raw_user_meta_data ->> 'academic_email')), ''),
    lower(trim(coalesce(u.email, '')))
  ) AS email,
  lower(trim(coalesce(u.email, ''))) AS internal_auth_email,
  (
    SELECT sp.id
    FROM public.study_programs sp
    WHERE upper(sp.code) = upper(trim(coalesce(u.raw_user_meta_data ->> 'study_program_code', '')))
    LIMIT 1
  ) AS study_program_id,
  coalesce(nullif(u.raw_user_meta_data ->> 'semester', '')::INTEGER, 1) AS semester,
  coalesce(nullif(u.raw_user_meta_data ->> 'cohort_year', '')::INTEGER, 2025) AS cohort_year,
  'student' AS role,
  'active' AS account_status,
  coalesce(nullif(lower(trim(u.raw_user_meta_data ->> 'student_badge_status')), ''), 'neutral') AS student_badge_status,
  coalesce((u.raw_user_meta_data ->> 'must_change_password')::BOOLEAN, FALSE) AS must_change_password,
  coalesce(u.created_at, NOW()) AS created_at,
  NOW() AS updated_at
FROM auth.users u
WHERE NOT EXISTS (
  SELECT 1 FROM public.profiles p WHERE p.id = u.id
)
AND lower(trim(coalesce(u.raw_user_meta_data ->> 'role', 'student'))) <> 'admin'
AND lower(coalesce(u.email, '')) NOT LIKE 'admin_%@auth.azrilclas.internal'
AND lower(coalesce(u.email, '')) <> 'admin@azrilclas.ac.id'
ON CONFLICT (id) DO NOTHING;

-- 8. Pastikan kolom `internal_auth_email` terisi untuk profil yang sudah ada agar konsisten dengan auth.users
UPDATE public.profiles p
SET internal_auth_email = lower(trim(u.email))
FROM auth.users u
WHERE u.id = p.id
  AND (p.internal_auth_email IS NULL OR trim(p.internal_auth_email) = '')
  AND u.email IS NOT NULL;

-- =============================================================================
-- PANDUAN OPSIONAL (JALANKAN MANUAL HANYA JIKA AKUN ADMIN BELUM TERTAUT DI PROFILES):
-- Jangan mengubah password atau menghapus akun Admin. Cukup tautkan user Admin
-- yang SUDAH ADA di Supabase Authentication -> Users ke tabel `public.profiles`:
--
--   INSERT INTO public.profiles (id, full_name, username, email, internal_auth_email, role, account_status)
--   SELECT id, 'Administrator Utama Azril Clas', 'Azril12v', lower(email), lower(email), 'admin', 'active'
--   FROM auth.users
--   WHERE lower(email) = lower('EMAIL_ADMIN_SUPABASE_ANDA@contoh.com')
--   ON CONFLICT (id) DO UPDATE
--     SET role = 'admin',
--         username = coalesce(nullif(public.profiles.username, ''), 'Azril12v'),
--         internal_auth_email = lower(EXCLUDED.internal_auth_email),
--         account_status = 'active',
--         updated_at = NOW();
-- =============================================================================
