-- ============================================================================
-- AZRIL CLAS - Migration 004: Sistem XP Akademik, XP Keaktifan, Login Streak,
--                             Notifikasi Pengingat Streak, & Riwayat XP
-- Safe, idempotent, non-destructive migration (preserves all existing data).
-- Timezone standard: Asia/Jakarta (WIB).
-- ============================================================================

-- 1. Semester & Izin Quota Settings
CREATE TABLE IF NOT EXISTS public.semester_xp_settings (
    semester_key TEXT PRIMARY KEY,              -- e.g., '2026/2027-Ganjil'
    academic_year TEXT NOT NULL,
    semester_name TEXT NOT NULL,
    max_paid_izin_per_semester INTEGER NOT NULL DEFAULT 2 CHECK (max_paid_izin_per_semester >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.semester_xp_settings (semester_key, academic_year, semester_name, max_paid_izin_per_semester, is_active)
VALUES ('2026/2027-Ganjil', '2026/2027', 'Ganjil', 2, TRUE)
ON CONFLICT (semester_key) DO NOTHING;

-- 2. Permanent XP Transactions Ledger (Single Source of Truth for Total XP)
CREATE TABLE IF NOT EXISTS public.xp_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    category TEXT NOT NULL CHECK (
        category IN (
            'ATTENDANCE_HADIR',
            'ATTENDANCE_IZIN',
            'ATTENDANCE_ALFA',
            'ASSIGNMENT_EARLY',
            'ASSIGNMENT_ON_TIME',
            'ASSIGNMENT_LATE',
            'PARTICIPATION_REGULAR',
            'PARTICIPATION_CRITICAL',
            'STREAK_BONUS',
            'MANUAL_ADJUSTMENT'
        )
    ),
    activity_title TEXT NOT NULL,
    xp_amount INTEGER NOT NULL,
    description TEXT NOT NULL,
    semester_key TEXT NOT NULL DEFAULT '2026/2027-Ganjil',
    reference_type TEXT NOT NULL CHECK (
        reference_type IN ('ATTENDANCE', 'ASSIGNMENT', 'PARTICIPATION', 'STREAK_MILESTONE', 'MANUAL_ADJUSTMENT')
    ),
    reference_id TEXT NOT NULL,
    admin_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Strict database-level anti-duplication guard per activity reference
    CONSTRAINT uq_xp_transactions_student_ref UNIQUE (student_id, reference_type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_xp_transactions_student_created
    ON public.xp_transactions (student_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_xp_transactions_category
    ON public.xp_transactions (student_id, category);
CREATE INDEX IF NOT EXISTS idx_xp_transactions_semester
    ON public.xp_transactions (student_id, semester_key);

-- 3. Student Login Streak State (Asia/Jakarta WIB)
CREATE TABLE IF NOT EXISTS public.student_streaks (
    student_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    current_streak INTEGER NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    longest_streak INTEGER NOT NULL DEFAULT 0 CHECK (longest_streak >= 0),
    last_login_date_wib DATE,
    streak_cycle_id UUID NOT NULL DEFAULT gen_random_uuid(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Daily Login Logs (max 1 row per student per WIB calendar date)
CREATE TABLE IF NOT EXISTS public.daily_login_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    login_date_wib DATE NOT NULL,
    logged_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_daily_login_student_date UNIQUE (student_id, login_date_wib)
);

CREATE INDEX IF NOT EXISTS idx_daily_login_logs_student_date
    ON public.daily_login_logs (student_id, login_date_wib DESC);

-- 5. Streak Milestone Achievements (prevents duplicate bonus within the same streak cycle)
CREATE TABLE IF NOT EXISTS public.streak_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    streak_cycle_id UUID NOT NULL,
    milestone_days INTEGER NOT NULL CHECK (milestone_days IN (3, 7, 14, 30)),
    bonus_xp INTEGER NOT NULL CHECK (bonus_xp > 0),
    achieved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_streak_milestone_cycle UNIQUE (student_id, streak_cycle_id, milestone_days)
);

-- 6. Streak Reminder Notification Logs (08:00, 12:00, 17:00 WIB - zero duplicates)
CREATE TABLE IF NOT EXISTS public.streak_notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reminder_date_wib DATE NOT NULL,
    reminder_slot TEXT NOT NULL CHECK (reminder_slot IN ('MORNING_08', 'NOON_12', 'EVENING_17')),
    message TEXT NOT NULL,
    channel TEXT NOT NULL DEFAULT 'IN_APP',
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_streak_reminder_unique UNIQUE (student_id, reminder_date_wib, reminder_slot)
);

CREATE INDEX IF NOT EXISTS idx_streak_notif_student_date
    ON public.streak_notification_logs (student_id, reminder_date_wib DESC);

-- 7. Server-Derived Total XP View (never trust frontend total XP)
CREATE OR REPLACE VIEW public.student_xp_totals AS
SELECT
    p.id AS student_id,
    COALESCE(SUM(x.xp_amount), 0)::INTEGER AS total_xp,
    COALESCE(
        SUM(
            CASE
                WHEN date_trunc('month', x.created_at AT TIME ZONE 'Asia/Jakarta') =
                     date_trunc('month', NOW() AT TIME ZONE 'Asia/Jakarta')
                THEN x.xp_amount
                ELSE 0
            END
        ),
        0
    )::INTEGER AS monthly_xp
FROM public.profiles p
LEFT JOIN public.xp_transactions x ON x.student_id = p.id
WHERE p.role = 'student'
GROUP BY p.id;

-- 8. Atomic Server Function: Record Daily Login Streak & Award Milestone Bonus (WIB)
CREATE OR REPLACE FUNCTION public.record_student_login_streak(p_student_id UUID DEFAULT auth.uid())
RETURNS TABLE (
    current_streak INTEGER,
    longest_streak INTEGER,
    logged_in_today BOOLEAN,
    awarded_milestone_days INTEGER,
    awarded_bonus_xp INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_today DATE := (NOW() AT TIME ZONE 'Asia/Jakarta')::DATE;
    v_yesterday DATE := v_today - INTERVAL '1 day';
    v_row public.student_streaks%ROWTYPE;
    v_new_streak INTEGER;
    v_new_longest INTEGER;
    v_cycle_id UUID;
    v_bonus_xp INTEGER := 0;
    v_milestone INTEGER := NULL;
    v_semester_key TEXT;
BEGIN
    IF p_student_id IS NULL OR (p_student_id <> auth.uid() AND NOT public.is_azril_admin()) THEN
        RAISE EXCEPTION 'Unauthorized streak update';
    END IF;

    SELECT semester_key INTO v_semester_key
    FROM public.semester_xp_settings
    WHERE is_active = TRUE
    ORDER BY updated_at DESC
    LIMIT 1;
    v_semester_key := COALESCE(v_semester_key, '2026/2027-Ganjil');

    -- Lock or initialize streak row to guarantee concurrency safety
    INSERT INTO public.student_streaks (student_id, current_streak, longest_streak, last_login_date_wib)
    VALUES (p_student_id, 0, 0, NULL)
    ON CONFLICT (student_id) DO NOTHING;

    SELECT * INTO v_row
    FROM public.student_streaks
    WHERE student_id = p_student_id
    FOR UPDATE;

    -- Already logged in today (WIB): idempotent no-op
    IF v_row.last_login_date_wib = v_today THEN
        RETURN QUERY SELECT v_row.current_streak, v_row.longest_streak, TRUE, NULL::INTEGER, 0;
        RETURN;
    END IF;

    IF v_row.last_login_date_wib = v_yesterday THEN
        v_new_streak := v_row.current_streak + 1;
        v_cycle_id := v_row.streak_cycle_id;
    ELSE
        v_new_streak := 1;
        v_cycle_id := gen_random_uuid();
    END IF;

    v_new_longest := GREATEST(v_row.longest_streak, v_new_streak);

    UPDATE public.student_streaks
    SET current_streak = v_new_streak,
        longest_streak = v_new_longest,
        last_login_date_wib = v_today,
        streak_cycle_id = v_cycle_id,
        updated_at = NOW()
    WHERE student_id = p_student_id;

    INSERT INTO public.daily_login_logs (student_id, login_date_wib, logged_in_at)
    VALUES (p_student_id, v_today, NOW())
    ON CONFLICT (student_id, login_date_wib) DO NOTHING;

    -- Check streak milestone bonuses (3 -> 10, 7 -> 30, 14 -> 50, 30 -> 100)
    IF v_new_streak IN (3, 7, 14, 30) THEN
        v_milestone := v_new_streak;
        v_bonus_xp := CASE v_new_streak
            WHEN 3 THEN 10
            WHEN 7 THEN 30
            WHEN 14 THEN 50
            WHEN 30 THEN 100
        END;

        INSERT INTO public.streak_milestones (student_id, streak_cycle_id, milestone_days, bonus_xp)
        VALUES (p_student_id, v_cycle_id, v_milestone, v_bonus_xp)
        ON CONFLICT (student_id, streak_cycle_id, milestone_days) DO NOTHING;

        IF FOUND THEN
            INSERT INTO public.xp_transactions (
                student_id, category, activity_title, xp_amount, description,
                semester_key, reference_type, reference_id
            )
            VALUES (
                p_student_id,
                'STREAK_BONUS',
                'Bonus Login Streak ' || v_milestone || ' Hari',
                v_bonus_xp,
                'Pencapaian konsistensi login ' || v_milestone || ' hari berturut-turut (WIB)',
                v_semester_key,
                'STREAK_MILESTONE',
                v_cycle_id::TEXT || ':' || v_milestone::TEXT
            )
            ON CONFLICT (student_id, reference_type, reference_id) DO NOTHING;
        ELSE
            v_milestone := NULL;
            v_bonus_xp := 0;
        END IF;
    END IF;

    RETURN QUERY SELECT v_new_streak, v_new_longest, TRUE, v_milestone, v_bonus_xp;
END;
$$;

-- 9. Atomic Admin Function: Award Manual Participation XP (ONLY 10 XP Regular or 20 XP Critical)
CREATE OR REPLACE FUNCTION public.admin_award_participation_xp(
    p_student_id UUID,
    p_category TEXT,
    p_note TEXT DEFAULT '',
    p_semester_key TEXT DEFAULT '2026/2027-Ganjil'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_xp INTEGER;
    v_title TEXT;
    v_tx_id UUID := gen_random_uuid();
BEGIN
    IF NOT public.is_azril_admin() THEN
        RAISE EXCEPTION 'Hanya Admin yang dapat memberikan XP keaktifan.';
    END IF;

    IF p_category = 'PARTICIPATION_REGULAR' THEN
        v_xp := 10;
        v_title := 'Bertanya / Berpendapat Biasa';
    ELSIF p_category = 'PARTICIPATION_CRITICAL' THEN
        v_xp := 20;
        v_title := 'Bertanya / Berpendapat Kritis';
    ELSE
        RAISE EXCEPTION 'Kategori keaktifan tidak valid. Pilih PARTICIPATION_REGULAR atau PARTICIPATION_CRITICAL.';
    END IF;

    INSERT INTO public.xp_transactions (
        id, student_id, category, activity_title, xp_amount, description,
        semester_key, reference_type, reference_id, admin_id
    )
    VALUES (
        v_tx_id,
        p_student_id,
        p_category,
        v_title,
        v_xp,
        COALESCE(NULLIF(trim(p_note), ''), v_title || ' dalam diskusi kelas'),
        p_semester_key,
        'PARTICIPATION',
        v_tx_id::TEXT,
        auth.uid()
    );

    RETURN v_tx_id;
END;
$$;

-- 10. Server Scheduler Function: Dispatch Streak Reminders (08:00, 12:00, 17:00 WIB)
CREATE OR REPLACE FUNCTION public.dispatch_due_streak_reminders(p_slot TEXT)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_today DATE := (NOW() AT TIME ZONE 'Asia/Jakarta')::DATE;
    v_message TEXT;
    v_inserted INTEGER := 0;
BEGIN
    v_message := CASE p_slot
        WHEN 'MORNING_08' THEN 'Selamat pagi! 🔥 Jangan lupa nyalakan streak Azril Clas hari ini.'
        WHEN 'NOON_12' THEN 'Sudah login hari ini? Pertahankan konsistensi belajarmu!'
        WHEN 'EVENING_17' THEN 'Jangan sampai streak-mu terputus. Login sekarang!'
        ELSE NULL
    END;

    IF v_message IS NULL THEN
        RAISE EXCEPTION 'Invalid reminder slot: %', p_slot;
    END IF;

    WITH candidates AS (
        SELECT p.id AS student_id
        FROM public.profiles p
        WHERE p.role = 'student'
          AND COALESCE(p.account_status, 'active') = 'active'
          AND NOT EXISTS (
              SELECT 1 FROM public.daily_login_logs dl
              WHERE dl.student_id = p.id
                AND dl.login_date_wib = v_today
          )
    ),
    inserted AS (
        INSERT INTO public.streak_notification_logs (student_id, reminder_date_wib, reminder_slot, message)
        SELECT c.student_id, v_today, p_slot, v_message
        FROM candidates c
        ON CONFLICT (student_id, reminder_date_wib, reminder_slot) DO NOTHING
        RETURNING 1
    )
    SELECT COUNT(*) INTO v_inserted FROM inserted;

    RETURN v_inserted;
END;
$$;

-- 11. Strict Row Level Security (RLS)
ALTER TABLE public.semester_xp_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xp_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_streaks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_login_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.streak_milestones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.streak_notification_logs ENABLE ROW LEVEL SECURITY;

-- semester_xp_settings: readable by authenticated, managed by Admin
DROP POLICY IF EXISTS "auth_read_semester_xp_settings" ON public.semester_xp_settings;
CREATE POLICY "auth_read_semester_xp_settings" ON public.semester_xp_settings
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "admin_manage_semester_xp_settings" ON public.semester_xp_settings;
CREATE POLICY "admin_manage_semester_xp_settings" ON public.semester_xp_settings
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- xp_transactions: Students can ONLY SELECT their own rows (no INSERT/UPDATE/DELETE). Admin has full access.
DROP POLICY IF EXISTS "student_read_own_xp_transactions" ON public.xp_transactions;
CREATE POLICY "student_read_own_xp_transactions" ON public.xp_transactions
    FOR SELECT TO authenticated
    USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_xp_transactions" ON public.xp_transactions;
CREATE POLICY "admin_manage_xp_transactions" ON public.xp_transactions
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- student_streaks: Students read own, Admin reads/manages all
DROP POLICY IF EXISTS "student_read_own_streak" ON public.student_streaks;
CREATE POLICY "student_read_own_streak" ON public.student_streaks
    FOR SELECT TO authenticated
    USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_streaks" ON public.student_streaks;
CREATE POLICY "admin_manage_streaks" ON public.student_streaks
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- daily_login_logs: Students read own, Admin reads all
DROP POLICY IF EXISTS "student_read_own_daily_login_logs" ON public.daily_login_logs;
CREATE POLICY "student_read_own_daily_login_logs" ON public.daily_login_logs
    FOR SELECT TO authenticated
    USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_daily_login_logs" ON public.daily_login_logs;
CREATE POLICY "admin_manage_daily_login_logs" ON public.daily_login_logs
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- streak_milestones: Students read own, Admin reads all
DROP POLICY IF EXISTS "student_read_own_streak_milestones" ON public.streak_milestones;
CREATE POLICY "student_read_own_streak_milestones" ON public.streak_milestones
    FOR SELECT TO authenticated
    USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_streak_milestones" ON public.streak_milestones;
CREATE POLICY "admin_manage_streak_milestones" ON public.streak_milestones
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- streak_notification_logs: Students read & mark-read own; Admin reads/manages all
DROP POLICY IF EXISTS "student_read_own_streak_notifications" ON public.streak_notification_logs;
CREATE POLICY "student_read_own_streak_notifications" ON public.streak_notification_logs
    FOR SELECT TO authenticated
    USING (student_id = auth.uid());

DROP POLICY IF EXISTS "admin_manage_streak_notifications" ON public.streak_notification_logs;
CREATE POLICY "admin_manage_streak_notifications" ON public.streak_notification_logs
    FOR ALL TO authenticated
    USING (public.is_azril_admin())
    WITH CHECK (public.is_azril_admin());

-- Optional pg_cron schedule (run in Supabase SQL Editor if pg_cron extension is enabled):
-- SELECT cron.schedule('azril-streak-08-wib', '0 1 * * *', $$SELECT public.dispatch_due_streak_reminders('MORNING_08');$$);
-- SELECT cron.schedule('azril-streak-12-wib', '0 5 * * *', $$SELECT public.dispatch_due_streak_reminders('NOON_12');$$);
-- SELECT cron.schedule('azril-streak-17-wib', '0 10 * * *', $$SELECT public.dispatch_due_streak_reminders('EVENING_17');$$);
