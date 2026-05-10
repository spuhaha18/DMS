DO $$
DECLARE
    v_sop_id     BIGINT;
    v_method_id  BIGINT;
    v_spec_id    BIGINT;
    v_form_id    BIGINT;
    v_tpl_id     BIGINT;
BEGIN
    SELECT id INTO v_sop_id    FROM document_categories WHERE category_code = 'SOP';
    IF v_sop_id IS NULL THEN RAISE EXCEPTION 'V17 시드 실패: SOP 카테고리를 찾을 수 없습니다'; END IF;

    SELECT id INTO v_method_id FROM document_categories WHERE category_code = 'METHOD';
    IF v_method_id IS NULL THEN RAISE EXCEPTION 'V17 시드 실패: METHOD 카테고리를 찾을 수 없습니다'; END IF;

    SELECT id INTO v_spec_id   FROM document_categories WHERE category_code = 'SPEC';
    IF v_spec_id IS NULL THEN RAISE EXCEPTION 'V17 시드 실패: SPEC 카테고리를 찾을 수 없습니다'; END IF;

    SELECT id INTO v_form_id   FROM document_categories WHERE category_code = 'FORM';
    IF v_form_id IS NULL THEN RAISE EXCEPTION 'V17 시드 실패: FORM 카테고리를 찾을 수 없습니다'; END IF;

    -- SOP: REVIEW(REVIEWER, parallel, min_signers=2) → APPROVAL(APPROVER) → APPROVAL(QA, qa_required)
    INSERT INTO workflow_templates(category_id, template_name, created_by, updated_by)
    VALUES (v_sop_id, 'SOP 표준 결재 (검토 2인 + 승인 + QA)', 'admin', 'admin')
    RETURNING id INTO v_tpl_id;
    INSERT INTO workflow_template_steps(template_id, step_order, step_type, role_code, min_signers, parallel, auto_assign, qa_required) VALUES
      (v_tpl_id, 1, 'REVIEW',   'REVIEWER', 2, TRUE,  TRUE, FALSE),
      (v_tpl_id, 2, 'APPROVAL', 'APPROVER', 1, FALSE, TRUE, FALSE),
      (v_tpl_id, 3, 'APPROVAL', 'QA',       1, FALSE, TRUE, TRUE);

    -- METHOD
    INSERT INTO workflow_templates(category_id, template_name, created_by, updated_by)
    VALUES (v_method_id, '시험방법 결재 (검토 + 승인 + QA)', 'admin', 'admin')
    RETURNING id INTO v_tpl_id;
    INSERT INTO workflow_template_steps(template_id, step_order, step_type, role_code, min_signers, parallel, auto_assign, qa_required) VALUES
      (v_tpl_id, 1, 'REVIEW',   'REVIEWER', 1, FALSE, TRUE, FALSE),
      (v_tpl_id, 2, 'APPROVAL', 'APPROVER', 1, FALSE, TRUE, FALSE),
      (v_tpl_id, 3, 'APPROVAL', 'QA',       1, FALSE, TRUE, TRUE);

    -- SPEC
    INSERT INTO workflow_templates(category_id, template_name, created_by, updated_by)
    VALUES (v_spec_id, '제품규격 결재 (검토 + 승인 + QA)', 'admin', 'admin')
    RETURNING id INTO v_tpl_id;
    INSERT INTO workflow_template_steps(template_id, step_order, step_type, role_code, min_signers, parallel, auto_assign, qa_required) VALUES
      (v_tpl_id, 1, 'REVIEW',   'REVIEWER', 1, FALSE, TRUE, FALSE),
      (v_tpl_id, 2, 'APPROVAL', 'APPROVER', 1, FALSE, TRUE, FALSE),
      (v_tpl_id, 3, 'APPROVAL', 'QA',       1, FALSE, TRUE, TRUE);

    -- FORM: 단일 승인
    INSERT INTO workflow_templates(category_id, template_name, created_by, updated_by)
    VALUES (v_form_id, '서식 단일 승인', 'admin', 'admin')
    RETURNING id INTO v_tpl_id;
    INSERT INTO workflow_template_steps(template_id, step_order, step_type, role_code, min_signers, parallel, auto_assign, qa_required) VALUES
      (v_tpl_id, 1, 'APPROVAL', 'APPROVER', 1, FALSE, TRUE, FALSE);
END $$;
