-- ============================================================================
-- AZRIL CLAS - Migration 007: Integrated Assignment Submissions, Individual
--                             Admin Verification Checklists, Backend WIB
--                             Deadline Enforcement, & Storage/RLS Hardening
-- Safe, idempotent, non-destructive migration.
-- Preserves all existing rows in public.assignments, public.assignment_targets,
-- public.profiles, public.classes, and public.study_programs.
-- ============================================================================

-- 1. Helper: Server UTC timestamp (for consistent WIB deadline sync on client)
CREATE OR REPLACE FUNCTION public.get_server_time_utc()
RETURNS TIMESTAMPTZ
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT NOW();
$$;

REVOKE ALL ON FUNCTION public.get_server_time_utc() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_server_time_utc() TO anon, authenticated;

-- 2. Helper: Determine whether a student is targeted by an active assignment
--    Supports both multi-class targeting (by class_id or class name) and
--    study_program targeting (by study_program_id or study_program_code).
CREATE OR REPLACE FUNCTION public.is_student_targeted_for_assignment(
    p_assignment_id TEXT,
    p_student_id UUID
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.assignments a
        JOIN public.profiles me ON me.id = p_student_id
        LEFT JOIN public.classes stu_cls ON stu_cls.id = me.class_id
        LEFT JOIN public.study_programs stu_sp ON stu_sp.id = me.study_program_id
        WHERE a.id = p_assignment_id
          AND a.is_active = TRUE
          AND me.role = 'student'
          AND COALESCE(me.account_status, 'active') = 'active'
          AND EXISTS (
              SELECT 1
              FROM public.assignment_targets t
              LEFT JOIN public.classes tgt_cls ON tgt_cls.id = t.class_id
              WHERE t.assignment_id = a.id
                AND (
                    (
                        a.target_type = 'class'
                        AND t.class_id IS NOT NULL
                        AND (
                            t.class_id = me.class_id
                            OR (
                                stu_cls.name IS NOT NULL
                                AND tgt_cls.name IS NOT NULL
                                AND upper(trim(stu_cls.name)) = upper(trim(tgt_cls.name))
                            )
                        )
                    )
                    OR (
                        a.target_type = 'study_program'
                        AND (
                            (t.study_program_id IS NOT NULL AND t.study_program_id = me.study_program_id)
                            OR (
                                t.study_program_code IS NOT NULL
                                AND stu_sp.code IS NOT NULL
                                AND upper(trim(stu_sp.code)) = upper(trim(t.study_program_code))
                            )
                        )
                    )
                )
          )
    );
$$;

REVOKE ALL ON FUNCTION public.is_student_targeted_for_assignment(TEXT, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_student_targeted_for_assignment(TEXT, UUID) TO authenticated;

-- 3. Student Submissions Table (1 unique submission per assignment + student)
CREATE TABLE IF NOT EXISTS public.assignment_submissions (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    assignment_id TEXT NOT NULL REFERENCES public.assignments(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    answer_text TEXT NOT NULL DEFAULT '',
    submission_url TEXT,
    file_name TEXT,
    file_url TEXT,
    file_size_label TEXT,
    status TEXT NOT NULL DEFAULT 'awaiting_admin' CHECK (
        status IN ('submitted', 'awaiting_admin', 'needs_revision', 'graded')
    ),
    revision_count INTEGER NOT NULL DEFAULT 0,
    first_submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    final_score INTEGER CHECK (final_score IS NULL OR (final_score >= 0 AND final_score <= 100)),
    admin_feedback TEXT,
    CONSTRAINT uq_assignment_submissions_per_student UNIQUE (assignment_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_assignment_submissions_assignment
    ON public.assignment_submissions (assignment_id);
CREATE INDEX IF NOT EXISTS idx_assignment_submissions_student
    ON public.assignment_submissions (student_id);

-- 4. Individual Admin Verification Checklist Table (1 row per assignment + student)
CREATE TABLE IF NOT EXISTS public.assignment_student_checklists (
    assignment_id TEXT NOT NULL REFERENCES public.assignments(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    verified_by_name TEXT,
    verified_at TIMESTAMPTZ,
    verification_note TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (assignment_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_assignment_checklists_student
    ON public.assignment_student_checklists (student_id);
CREATE INDEX IF NOT EXISTS idx_assignment_checklists_assignment_completed
    ON public.assignment_student_checklists (assignment_id, is_completed);

-- 5. Backend Trigger on assignment_submissions:
--    - Enforces server-time deadline validation (NOW() <= assignments.deadline)
--    - Prevents student from submitting for another student or untargeted assignment
--    - Preserves first_submitted_at and increments revision_count on update
--    - Blocks students from altering final_score or admin_feedback
CREATE OR REPLACE FUNCTION public.enforce_assignment_submission_rules()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_deadline TIMESTAMPTZ;
    v_is_active BOOLEAN;
    v_has_content BOOLEAN;
BEGIN
    -- Service role (auth.uid() IS NULL) and verified Admin bypass student deadline lock
    IF auth.uid() IS NULL OR public.is_azril_admin() THEN
        NEW.last_updated_at := NOW();
        RETURN NEW;
    END IF;

    -- Student ownership check
    IF NEW.student_id IS DISTINCT FROM auth.uid() THEN
        RAISE EXCEPTION 'Akses ditolak: mahasiswa hanya dapat mengelola pengumpulan miliknya sendiri.';
    END IF;

    -- Targeting check
    IF NOT public.is_student_targeted_for_assignment(NEW.assignment_id, auth.uid()) THEN
        RAISE EXCEPTION 'Akses ditolak: tugas ini tidak ditujukan untuk kelas atau program studi Anda.';
    END IF;

    -- Server-time deadline check (PostgreSQL TIMESTAMPTZ NOW(), consistent across Asia/Jakarta WIB)
    SELECT deadline, is_active
    INTO v_deadline, v_is_active
    FROM public.assignments
    WHERE id = NEW.assignment_id;

    IF v_deadline IS NULL OR NOT v_is_active THEN
        RAISE EXCEPTION 'Tugas tidak ditemukan atau sudah dinonaktifkan.';
    END IF;

    IF NOW() > v_deadline THEN
        RAISE EXCEPTION 'Deadline tugas telah berakhir (WIB). Pengumpulan atau pengeditan jawaban tidak lagi diizinkan.';
    END IF;

    v_has_content :=
        NULLIF(trim(COALESCE(NEW.answer_text, '')), '') IS NOT NULL
        OR NULLIF(trim(COALESCE(NEW.submission_url, '')), '') IS NOT NULL
        OR NULLIF(trim(COALESCE(NEW.file_name, '')), '') IS NOT NULL
        OR NULLIF(trim(COALESCE(NEW.file_url, '')), '') IS NOT NULL;

    IF NOT v_has_content THEN
        RAISE EXCEPTION 'Lampirkan jawaban teks, tautan URL, atau file tugas sebelum mengirim.';
    END IF;

    IF TG_OP = 'INSERT' THEN
        NEW.first_submitted_at := NOW();
        NEW.last_updated_at := NOW();
        NEW.revision_count := 0;
        NEW.status := 'awaiting_admin';
        NEW.final_score := NULL;
        NEW.admin_feedback := NULL;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
            OR NEW.student_id IS DISTINCT FROM OLD.student_id
        THEN
            RAISE EXCEPTION 'Identitas tugas dan mahasiswa tidak dapat diubah.';
        END IF;
        NEW.first_submitted_at := OLD.first_submitted_at;
        NEW.last_updated_at := NOW();
        NEW.revision_count := COALESCE(OLD.revision_count, 0) + 1;
        NEW.status := 'awaiting_admin';
        -- Prevent student tampering with admin grading fields
        NEW.final_score := OLD.final_score;
        NEW.admin_feedback := OLD.admin_feedback;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_assignment_submission_rules ON public.assignment_submissions;
CREATE TRIGGER trg_enforce_assignment_submission_rules
    BEFORE INSERT OR UPDATE ON public.assignment_submissions
    FOR EACH ROW
    EXECUTE FUNCTION public.enforce_assignment_submission_rules();

-- 6. Backend Trigger on assignment_student_checklists:
--    Strictly forbids ANY non-admin mutation even via direct SQL/API calls
CREATE OR REPLACE FUNCTION public.enforce_admin_only_assignment_checklist()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NOT NULL AND NOT public.is_azril_admin() THEN
        RAISE EXCEPTION 'Akses ditolak: hanya Administrator yang dapat mengubah checklist penyelesaian tugas.';
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_admin_only_assignment_checklist ON public.assignment_student_checklists;
CREATE TRIGGER trg_enforce_admin_only_assignment_checklist
    BEFORE INSERT OR UPDATE ON public.assignment_student_checklists
    FOR EACH ROW
    EXECUTE FUNCTION public.enforce_admin_only_assignment_checklist();

-- 7. Secure RPC: Student Submit / Edit Submission Before Server Deadline (Atomic Upsert)
CREATE OR REPLACE FUNCTION public.upsert_student_assignment_submission(
    p_assignment_id TEXT,
    p_answer_text TEXT DEFAULT '',
    p_submission_url TEXT DEFAULT NULL,
    p_file_name TEXT DEFAULT NULL,
    p_file_url TEXT DEFAULT NULL,
    p_file_size_label TEXT DEFAULT NULL
)
RETURNS public.assignment_submissions
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_row public.assignment_submissions;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Autentikasi diperlukan untuk mengumpulkan tugas.';
    END IF;

    INSERT INTO public.assignment_submissions (
        assignment_id,
        student_id,
        answer_text,
        submission_url,
        file_name,
        file_url,
        file_size_label,
        status
    )
    VALUES (
        p_assignment_id,
        v_uid,
        COALESCE(trim(p_answer_text), ''),
        NULLIF(trim(COALESCE(p_submission_url, '')), ''),
        NULLIF(trim(COALESCE(p_file_name, '')), ''),
        NULLIF(trim(COALESCE(p_file_url, '')), ''),
        NULLIF(trim(COALESCE(p_file_size_label, '')), ''),
        'awaiting_admin'
    )
    ON CONFLICT (assignment_id, student_id)
    DO UPDATE SET
        answer_text = EXCLUDED.answer_text,
        submission_url = EXCLUDED.submission_url,
        file_name = EXCLUDED.file_name,
        file_url = EXCLUDED.file_url,
        file_size_label = EXCLUDED.file_size_label
    RETURNING * INTO v_row;

    RETURN v_row;
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_student_assignment_submission(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_student_assignment_submission(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

-- 8. Secure RPC: Admin-Only Individual Checklist Toggle
CREATE OR REPLACE FUNCTION public.admin_set_assignment_checklist(
    p_assignment_id TEXT,
    p_student_id UUID,
    p_is_completed BOOLEAN,
    p_note TEXT DEFAULT NULL
)
RETURNS public.assignment_student_checklists
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_admin_name TEXT;
    v_row public.assignment_student_checklists;
BEGIN
    IF auth.uid() IS NULL OR NOT public.is_azril_admin() THEN
        RAISE EXCEPTION 'Akses ditolak: hanya Administrator yang dapat memberikan atau membatalkan checklist penyelesaian.';
    END IF;

    SELECT full_name INTO v_admin_name
    FROM public.profiles
    WHERE id = auth.uid();

    INSERT INTO public.assignment_student_checklists (
        assignment_id,
        student_id,
        is_completed,
        verified_by,
        verified_by_name,
        verified_at,
        verification_note,
        updated_at
    )
    VALUES (
        p_assignment_id,
        p_student_id,
        p_is_completed,
        CASE WHEN p_is_completed THEN auth.uid() ELSE NULL END,
        CASE WHEN p_is_completed THEN COALESCE(v_admin_name, 'Administrator') ELSE NULL END,
        CASE WHEN p_is_completed THEN NOW() ELSE NULL END,
        NULLIF(trim(COALESCE(p_note, '')), ''),
        NOW()
    )
    ON CONFLICT (assignment_id, student_id)
    DO UPDATE SET
        is_completed = EXCLUDED.is_completed,
        verified_by = EXCLUDED.verified_by,
        verified_by_name = EXCLUDED.verified_by_name,
        verified_at = EXCLUDED.verified_at,
        verification_note = EXCLUDED.verification_note,
        updated_at = NOW()
    RETURNING * INTO v_row;

    RETURN v_row;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_set_assignment_checklist(TEXT, UUID, BOOLEAN, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_set_assignment_checklist(TEXT, UUID, BOOLEAN, TEXT) TO authenticated;

-- 9. Strict Row Level Security (RLS) on Assignments, Submissions, & Checklists
ALTER TABLE public.assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_targets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_student_checklists ENABLE ROW LEVEL SECURITY;

-- Refresh student read policy on public.assignments to use unified targeting helper
DROP POLICY IF EXISTS "Students can read targeted active assignments" ON public.assignments;
CREATE POLICY "Students can read targeted active assignments"
ON public.assignments
FOR SELECT
TO authenticated
USING (
    public.is_student_targeted_for_assignment(id, auth.uid())
);

-- Refresh student read policy on public.assignment_targets (includes study_program_code match)
DROP POLICY IF EXISTS "Students can read matching assignment targets" ON public.assignment_targets;
CREATE POLICY "Students can read matching assignment targets"
ON public.assignment_targets
FOR SELECT
TO authenticated
USING (
    public.is_student_targeted_for_assignment(assignment_id, auth.uid())
);

-- Submissions RLS:
-- Admin: full access
DROP POLICY IF EXISTS "admin_manage_all_submissions" ON public.assignment_submissions;
CREATE POLICY "admin_manage_all_submissions"
ON public.assignment_submissions
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

-- Student: read ONLY own submissions
DROP POLICY IF EXISTS "student_read_own_submissions" ON public.assignment_submissions;
CREATE POLICY "student_read_own_submissions"
ON public.assignment_submissions
FOR SELECT
TO authenticated
USING (student_id = auth.uid());

-- Student: insert ONLY own submission for targeted assignment BEFORE server deadline
DROP POLICY IF EXISTS "student_insert_own_submission_before_deadline" ON public.assignment_submissions;
CREATE POLICY "student_insert_own_submission_before_deadline"
ON public.assignment_submissions
FOR INSERT
TO authenticated
WITH CHECK (
    student_id = auth.uid()
    AND public.is_student_targeted_for_assignment(assignment_id, auth.uid())
    AND EXISTS (
        SELECT 1
        FROM public.assignments a
        WHERE a.id = assignment_submissions.assignment_id
          AND a.is_active = TRUE
          AND NOW() <= a.deadline
    )
);

-- Student: update ONLY own submission BEFORE server deadline
DROP POLICY IF EXISTS "student_update_own_submission_before_deadline" ON public.assignment_submissions;
CREATE POLICY "student_update_own_submission_before_deadline"
ON public.assignment_submissions
FOR UPDATE
TO authenticated
USING (student_id = auth.uid())
WITH CHECK (
    student_id = auth.uid()
    AND public.is_student_targeted_for_assignment(assignment_id, auth.uid())
    AND EXISTS (
        SELECT 1
        FROM public.assignments a
        WHERE a.id = assignment_submissions.assignment_id
          AND a.is_active = TRUE
          AND NOW() <= a.deadline
    )
);

-- Checklists RLS:
-- Admin: full access
DROP POLICY IF EXISTS "admin_manage_all_checklists" ON public.assignment_student_checklists;
CREATE POLICY "admin_manage_all_checklists"
ON public.assignment_student_checklists
FOR ALL
TO authenticated
USING (public.is_azril_admin())
WITH CHECK (public.is_azril_admin());

-- Student: READ-ONLY access to own checklist row ONLY (NO insert/update/delete)
DROP POLICY IF EXISTS "student_read_own_checklists" ON public.assignment_student_checklists;
CREATE POLICY "student_read_own_checklists"
ON public.assignment_student_checklists
FOR SELECT
TO authenticated
USING (student_id = auth.uid());

-- 10. Supabase Storage Bucket & Policies for Student Submission Files
--     Path convention: {assignment_id}/{student_id}/{filename}
INSERT INTO storage.buckets (id, name, public)
VALUES ('assignment-submissions', 'assignment-submissions', FALSE)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "admin_full_access_assignment_submissions_storage" ON storage.objects;
CREATE POLICY "admin_full_access_assignment_submissions_storage"
ON storage.objects
FOR ALL
TO authenticated
USING (
    bucket_id = 'assignment-submissions'
    AND public.is_azril_admin()
)
WITH CHECK (
    bucket_id = 'assignment-submissions'
    AND public.is_azril_admin()
);

DROP POLICY IF EXISTS "student_read_own_submission_files" ON storage.objects;
CREATE POLICY "student_read_own_submission_files"
ON storage.objects
FOR SELECT
TO authenticated
USING (
    bucket_id = 'assignment-submissions'
    AND (storage.foldername(name))[2] = auth.uid()::text
);

DROP POLICY IF EXISTS "student_upload_own_submission_files_before_deadline" ON storage.objects;
CREATE POLICY "student_upload_own_submission_files_before_deadline"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'assignment-submissions'
    AND (storage.foldername(name))[2] = auth.uid()::text
    AND EXISTS (
        SELECT 1
        FROM public.assignments a
        WHERE a.id = (storage.foldername(name))[1]
          AND a.is_active = TRUE
          AND NOW() <= a.deadline
    )
);

DROP POLICY IF EXISTS "student_update_own_submission_files_before_deadline" ON storage.objects;
CREATE POLICY "student_update_own_submission_files_before_deadline"
ON storage.objects
FOR UPDATE
TO authenticated
USING (
    bucket_id = 'assignment-submissions'
    AND (storage.foldername(name))[2] = auth.uid()::text
)
WITH CHECK (
    bucket_id = 'assignment-submissions'
    AND (storage.foldername(name))[2] = auth.uid()::text
    AND EXISTS (
        SELECT 1
        FROM public.assignments a
        WHERE a.id = (storage.foldername(name))[1]
          AND a.is_active = TRUE
          AND NOW() <= a.deadline
    )
);

-- 11. Enable Supabase Realtime publication for live Admin <-> Student sync (idempotent)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.assignments;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.assignment_submissions;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.assignment_student_checklists;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
    END IF;
END $$;
