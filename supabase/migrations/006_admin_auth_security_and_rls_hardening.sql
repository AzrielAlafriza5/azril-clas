-- ============================================================================
-- AZRIL CLAS - Migration 006: Admin Authentication Security, Role Separation,
--                             & Comprehensive RLS Hardening
-- Safe, idempotent, non-destructive migration (preserves all existing data
-- and creates NO duplicate user/role tables).
-- ============================================================================

-- 1. Harden is_azril_admin() to verify BOTH role = 'admin' AND active status
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
          AND COALESCE(p.account_status, 'active') = 'active'
    );
$$;

REVOKE ALL ON FUNCTION public.is_azril_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_azril_admin() TO authenticated;

-- 2. Dedicated backend-verified Admin session check RPC
-- Called by client & middleware to verify the current JWT belongs to an active Admin
CREATE OR REPLACE FUNCTION public.verify_admin_session()
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RETURN FALSE;
    END IF;
    RETURN public.is_azril_admin();
END;
$$;

REVOKE ALL ON FUNCTION public.verify_admin_session() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.verify_admin_session() TO authenticated;

-- 3. Dedicated username-to-auth-email resolver STRICTLY for Admin login
-- Only resolves active accounts where role = 'admin'. Student accounts return NULL.
CREATE OR REPLACE FUNCTION public.resolve_admin_login_email(p_username TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
    v_clean TEXT := lower(trim(p_username));
BEGIN
    IF v_clean = '' THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(NULLIF(trim(internal_auth_email), ''), email)
    INTO v_email
    FROM public.profiles
    WHERE role = 'admin'
      AND COALESCE(account_status, 'active') = 'active'
      AND lower(trim(username)) = v_clean
    LIMIT 1;

    RETURN v_email;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_admin_login_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_admin_login_email(TEXT) TO anon, authenticated;

-- 4. Harden student login resolver so Admin accounts cannot authenticate via Student login flow
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
    v_clean TEXT := lower(trim(p_identifier));
BEGIN
    SELECT COALESCE(NULLIF(trim(internal_auth_email), ''), email),
           COALESCE(account_status, 'active'),
           role
    INTO v_email, v_status, v_role
    FROM public.profiles
    WHERE lower(trim(nim)) = v_clean
       OR lower(trim(username)) = v_clean
       OR lower(trim(email)) = v_clean
       OR lower(trim(internal_auth_email)) = v_clean
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

-- 5. Anti-Privilege-Escalation Trigger on public.profiles
-- Guarantees that NO non-admin authenticated user can ever elevate role or tamper with
-- NIM, study_program_id, class_id, account_status, or student_badge_status.
CREATE OR REPLACE FUNCTION public.prevent_profile_privilege_escalation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Allow service_role (auth.uid() IS NULL in direct service operations) and verified admins
    IF auth.uid() IS NULL OR public.is_azril_admin() THEN
        RETURN NEW;
    END IF;

    IF NEW.role IS DISTINCT FROM OLD.role
        OR NEW.account_status IS DISTINCT FROM OLD.account_status
        OR NEW.nim IS DISTINCT FROM OLD.nim
        OR NEW.study_program_id IS DISTINCT FROM OLD.study_program_id
        OR NEW.class_id IS DISTINCT FROM OLD.class_id
        OR NEW.student_badge_status IS DISTINCT FROM OLD.student_badge_status
    THEN
        RAISE EXCEPTION 'Akses ditolak: perubahan hak akses atau data akademik hanya dapat dilakukan oleh Administrator.';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_profile_privilege_escalation ON public.profiles;
CREATE TRIGGER trg_prevent_profile_privilege_escalation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_profile_privilege_escalation();

-- 6. Admin Security & Authentication Audit Log Table (Admin-only read via RLS)
CREATE TABLE IF NOT EXISTS public.admin_auth_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempted_username TEXT NOT NULL,
    actor_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('ADMIN_LOGIN_SUCCESS', 'ADMIN_LOGIN_FAILED', 'UNAUTHORIZED_ADMIN_ACCESS', 'ADMIN_LOGOUT')
    ),
    detail TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_auth_audit_created
    ON public.admin_auth_audit_logs (created_at DESC);

ALTER TABLE public.admin_auth_audit_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_read_auth_audit_logs" ON public.admin_auth_audit_logs;
CREATE POLICY "admin_read_auth_audit_logs"
ON public.admin_auth_audit_logs
FOR SELECT
TO authenticated
USING (public.is_azril_admin());

-- 7. Clean up legacy/permissive profile RLS policies & ensure non-recursive is_azril_admin() across tables
DROP POLICY IF EXISTS "student_update_own_profile" ON public.profiles;
DROP POLICY IF EXISTS "admin_manage_profiles" ON public.profiles;
DROP POLICY IF EXISTS "Admins can manage all profiles" ON public.profiles;

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

-- Refresh study_programs & ranking policies to use SECURITY DEFINER public.is_azril_admin()
DROP POLICY IF EXISTS "admin_manage_study_programs" ON public.study_programs;
CREATE POLICY "admin_manage_study_programs"
ON public.study_programs
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "admin_manage_ranking_points" ON public.ranking_points;
CREATE POLICY "admin_manage_ranking_points"
ON public.ranking_points
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

DROP POLICY IF EXISTS "admin_manage_ranking_audit_logs" ON public.ranking_point_audit_logs;
CREATE POLICY "admin_manage_ranking_audit_logs"
ON public.ranking_point_audit_logs
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

-- Enforce RLS across all existing sensitive tables (idempotent safety check)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_programs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_targets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ranking_points ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ranking_point_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.semester_xp_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xp_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_streaks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_login_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.streak_milestones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.streak_notification_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_import_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.historical_attendance_records ENABLE ROW LEVEL SECURITY;

-- NOTE ON PROVISIONING PRIMARY ADMIN ACCOUNT:
-- Never insert plaintext passwords in SQL migrations.
-- Provision the primary administrator (username: Azril12v) via the secure Edge Function
-- `supabase/functions/admin-auth-login` (action: "bootstrap_primary_admin") with
-- PRIMARY_ADMIN_USERNAME, PRIMARY_ADMIN_PASSWORD, and ADMIN_BOOTSTRAP_SECRET
-- configured strictly in Supabase Server Secrets.
