-- =============================================================================
-- Azril Clas — Migration 010: Unified Leaderboard, XP Sync & Admin Visibility
-- =============================================================================
-- Tujuan (100% non-destruktif, idempoten, tanpa tabel duplikat, RLS tetap aktif):
-- 1. Menyediakan RPC SECURITY DEFINER `get_student_leaderboard_directory()`
--    agar Dashboard Mahasiswa (Ranking & Podium Top 3) dan Dashboard Admin
--    membaca sumber data mahasiswa & XP yang SAMA dari `public.profiles`,
--    `public.study_programs`, `public.classes`, `public.xp_transactions`,
--    dan `public.ranking_points` tanpa membuka data sensitif atau mematikan RLS.
-- 2. Menyediakan RPC `get_admin_directory_students()` (khusus Admin terverifikasi)
--    sebagai jalur baca tangguh untuk semua mahasiswa aktif maupun legacy.
-- 3. Memastikan policy RLS pada `xp_transactions`, `ranking_points`,
--    `student_streaks`, `classes`, dan `study_programs` non-rekursif via
--    `public.is_azril_admin()`.
-- =============================================================================

-- 1. Pastikan RLS tetap aktif pada tabel-tabel utama
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_programs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF to_regclass('public.xp_transactions') IS NOT NULL THEN
    ALTER TABLE public.xp_transactions ENABLE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS "xp_transactions_admin_all" ON public.xp_transactions;
    CREATE POLICY "xp_transactions_admin_all"
      ON public.xp_transactions
      FOR ALL
      TO authenticated
      USING (public.is_azril_admin())
      WITH CHECK (public.is_azril_admin());

    DROP POLICY IF EXISTS "xp_transactions_student_select_own" ON public.xp_transactions;
    CREATE POLICY "xp_transactions_student_select_own"
      ON public.xp_transactions
      FOR SELECT
      TO authenticated
      USING (student_id = auth.uid());
  END IF;

  IF to_regclass('public.ranking_points') IS NOT NULL THEN
    ALTER TABLE public.ranking_points ENABLE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS "admin_manage_ranking_points" ON public.ranking_points;
    CREATE POLICY "admin_manage_ranking_points"
      ON public.ranking_points
      FOR ALL
      TO authenticated
      USING (public.is_azril_admin())
      WITH CHECK (public.is_azril_admin());

    DROP POLICY IF EXISTS "student_read_own_ranking_points" ON public.ranking_points;
    CREATE POLICY "student_read_own_ranking_points"
      ON public.ranking_points
      FOR SELECT
      TO authenticated
      USING (student_id = auth.uid());
  END IF;

  IF to_regclass('public.student_streaks') IS NOT NULL THEN
    ALTER TABLE public.student_streaks ENABLE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS "student_streaks_admin_all" ON public.student_streaks;
    CREATE POLICY "student_streaks_admin_all"
      ON public.student_streaks
      FOR ALL
      TO authenticated
      USING (public.is_azril_admin())
      WITH CHECK (public.is_azril_admin());

    DROP POLICY IF EXISTS "student_streaks_select_own" ON public.student_streaks;
    CREATE POLICY "student_streaks_select_own"
      ON public.student_streaks
      FOR SELECT
      TO authenticated
      USING (student_id = auth.uid());
  END IF;
END $$;

-- 2. RPC Leaderboard terpadu untuk Mahasiswa & Admin (aman dengan RLS, tanpa email privat)
CREATE OR REPLACE FUNCTION public.get_student_leaderboard_directory()
RETURNS TABLE (
  id UUID,
  full_name TEXT,
  nim TEXT,
  study_program_id UUID,
  study_program_code TEXT,
  class_id TEXT,
  class_name TEXT,
  semester INTEGER,
  cohort_year INTEGER,
  account_status TEXT,
  student_badge_status TEXT,
  total_xp INTEGER
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Autentikasi diperlukan untuk memuat papan peringkat.';
  END IF;

  RETURN QUERY
  SELECT
    p.id,
    COALESCE(NULLIF(trim(p.full_name), ''), COALESCE(p.nim, 'Mahasiswa')) AS full_name,
    COALESCE(NULLIF(trim(p.nim), ''), '-') AS nim,
    p.study_program_id,
    COALESCE(sp.code, 'HES') AS study_program_code,
    p.class_id,
    c.name AS class_name,
    COALESCE(p.semester, 1) AS semester,
    COALESCE(p.cohort_year, 2025) AS cohort_year,
    COALESCE(p.account_status, 'active') AS account_status,
    COALESCE(p.student_badge_status, 'neutral') AS student_badge_status,
    GREATEST(
      COALESCE((SELECT SUM(x.xp_amount)::INTEGER FROM public.xp_transactions x WHERE x.student_id = p.id), 0),
      COALESCE((SELECT SUM(r.points)::INTEGER FROM public.ranking_points r WHERE r.student_id = p.id), 0)
    ) AS total_xp
  FROM public.profiles p
  LEFT JOIN public.study_programs sp ON sp.id = p.study_program_id
  LEFT JOIN public.classes c ON c.id = p.class_id
  WHERE lower(trim(COALESCE(p.role, 'student'))) <> 'admin'
  ORDER BY total_xp DESC, p.full_name ASC;
END;
$$;

REVOKE ALL ON FUNCTION public.get_student_leaderboard_directory() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_student_leaderboard_directory() TO authenticated, service_role;

-- 3. RPC Direktori Mahasiswa khusus Admin terverifikasi (termasuk data legacy tanpa filter ketat)
CREATE OR REPLACE FUNCTION public.get_admin_directory_students()
RETURNS TABLE (
  id UUID,
  full_name TEXT,
  nim TEXT,
  email TEXT,
  study_program_id UUID,
  study_program_code TEXT,
  class_id TEXT,
  class_name TEXT,
  semester INTEGER,
  cohort_year INTEGER,
  account_status TEXT,
  student_badge_status TEXT,
  must_change_password BOOLEAN,
  created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_azril_admin() THEN
    RAISE EXCEPTION 'Akses ditolak: hanya Administrator terverifikasi yang dapat membaca seluruh direktori mahasiswa.';
  END IF;

  RETURN QUERY
  SELECT
    p.id,
    COALESCE(NULLIF(trim(p.full_name), ''), COALESCE(p.nim, 'Mahasiswa')) AS full_name,
    COALESCE(NULLIF(trim(p.nim), ''), COALESCE(p.username, '-')) AS nim,
    COALESCE(trim(p.email), '') AS email,
    p.study_program_id,
    COALESCE(sp.code, 'HES') AS study_program_code,
    p.class_id,
    c.name AS class_name,
    COALESCE(p.semester, 1) AS semester,
    COALESCE(p.cohort_year, 2025) AS cohort_year,
    COALESCE(p.account_status, 'active') AS account_status,
    COALESCE(p.student_badge_status, 'neutral') AS student_badge_status,
    COALESCE(p.must_change_password, FALSE) AS must_change_password,
    p.created_at
  FROM public.profiles p
  LEFT JOIN public.study_programs sp ON sp.id = p.study_program_id
  LEFT JOIN public.classes c ON c.id = p.class_id
  WHERE lower(trim(COALESCE(p.role, 'student'))) <> 'admin'
  ORDER BY p.created_at DESC NULLS LAST, p.full_name ASC;
END;
$$;

REVOKE ALL ON FUNCTION public.get_admin_directory_students() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_admin_directory_students() TO authenticated, service_role;
