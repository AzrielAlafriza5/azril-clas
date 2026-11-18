-- Azril Clas — Student Ranking & Leaderboard PostgreSQL / Supabase Migration
-- Includes ranking_points ledger, unique anti-duplication constraint,
-- audit log table, automatic recalculation triggers, and zero-trust RLS policies.

CREATE TABLE IF NOT EXISTS public.ranking_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL CHECK (source_type IN ('ATTENDANCE', 'ASSIGNMENT', 'MANUAL_ADJUSTMENT')),
    source_id UUID NOT NULL,
    points INTEGER NOT NULL CHECK (points >= 0),
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ranking_points_student_source UNIQUE (student_id, source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_ranking_points_student_id ON public.ranking_points (student_id);
CREATE INDEX IF NOT EXISTS idx_ranking_points_created_at ON public.ranking_points (created_at);

CREATE TABLE IF NOT EXISTS public.ranking_point_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    admin_id UUID NOT NULL REFERENCES public.profiles(id),
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) >= 5),
    points_before INTEGER NOT NULL,
    points_after INTEGER NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ranking_audit_student_id ON public.ranking_point_audit_logs (student_id);

-- Automatic recalculation on attendance insert/update
CREATE OR REPLACE FUNCTION public.sync_attendance_ranking_points()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_points INTEGER;
BEGIN
    v_points := CASE NEW.status
        WHEN 'HADIR' THEN 10
        WHEN 'IZIN' THEN 5
        WHEN 'SAKIT' THEN 5
        ELSE 0
    END;

    INSERT INTO public.ranking_points (student_id, source_type, source_id, points, description, updated_at)
    VALUES (
        NEW.student_id,
        'ATTENDANCE',
        NEW.session_id,
        v_points,
        'Absensi sesi (' || NEW.status || ')',
        NOW()
    )
    ON CONFLICT (student_id, source_type, source_id)
    DO UPDATE SET
        points = EXCLUDED.points,
        description = EXCLUDED.description,
        updated_at = NOW();

    RETURN NEW;
END;
$$;

-- Automatic recalculation on submission insert/update (revisions update same row, zero duplication)
CREATE OR REPLACE FUNCTION public.sync_submission_ranking_points()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_points INTEGER;
BEGIN
    v_points := CASE
        WHEN NEW.status = 'LATE' THEN 5
        WHEN NEW.status = 'NOT_SUBMITTED' THEN 0
        ELSE 10
    END;

    INSERT INTO public.ranking_points (student_id, source_type, source_id, points, description, updated_at)
    VALUES (
        NEW.student_id,
        'ASSIGNMENT',
        NEW.assignment_id,
        v_points,
        CASE WHEN NEW.status = 'LATE' THEN 'Tugas dikumpulkan terlambat' ELSE 'Tugas dikumpulkan tepat waktu' END,
        NOW()
    )
    ON CONFLICT (student_id, source_type, source_id)
    DO UPDATE SET
        points = EXCLUDED.points,
        description = EXCLUDED.description,
        updated_at = NOW();

    RETURN NEW;
END;
$$;

-- Row Level Security (RLS)
ALTER TABLE public.ranking_points ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ranking_point_audit_logs ENABLE ROW LEVEL SECURITY;

-- Admin has full read/write visibility
CREATE POLICY "admin_manage_ranking_points" ON public.ranking_points
    FOR ALL
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'));

-- Students can only read their own point ledger (never modify directly)
CREATE POLICY "student_read_own_ranking_points" ON public.ranking_points
    FOR SELECT
    USING (student_id = auth.uid());

-- Only Admin can read/insert manual point audit logs
CREATE POLICY "admin_manage_ranking_audit_logs" ON public.ranking_point_audit_logs
    FOR ALL
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = auth.uid() AND p.role = 'admin'));
