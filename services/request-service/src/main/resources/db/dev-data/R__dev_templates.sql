-- Sample request templates for local development and demos. Not loaded in production.
-- Department codes and term values match org-service's dev data. Each block inserts a template only if
-- no template with that title exists, so re-running this migration never creates duplicates.

-- 1. Student status confirmation: one step at the Academic Affairs Office.
with template as (
    insert into request_template (title, description, department_code, audience, status, show_processing_time)
    select 'Student status confirmation',
           'Confirms that you are currently enrolled, for military service deferral, bank loans and similar.',
           'ACAD', 'STUDENT', 'ACTIVE', true
    where not exists (select 1 from request_template where title = 'Student status confirmation')
    returning id
), fields as (
    insert into template_field (template_id, position, field_key, label, field_type, required, options, hint)
    select template.id, f.position, f.field_key, f.label, f.field_type, f.required, f.options, f.hint
    from template, (values
        (1, 'purpose', 'Purpose', 'SELECT', true,
            array['Military service deferral', 'Student loan', 'Dormitory registration', 'Other']::text[], null),
        (2, 'term', 'Term', 'TERM', true, '{}'::text[], 'The term the confirmation is for'),
        (3, 'note', 'Note', 'TEXTAREA', false, '{}'::text[], 'Anything the office should know')
    ) as f (position, field_key, label, field_type, required, options, hint)
)
insert into template_step (template_id, position, department_code, description, sla_days, requires_attachment)
select template.id, 1, 'ACAD', 'Verify enrollment and issue the confirmation', 2, false from template;

-- 2. Tuition fee receipt: one step at the Finance Office, with a payment proof upload.
with template as (
    insert into request_template (title, description, department_code, audience, status, show_processing_time)
    select 'Tuition fee receipt', 'Official receipt for tuition paid by bank transfer.',
           'FIN', 'STUDENT', 'ACTIVE', true
    where not exists (select 1 from request_template where title = 'Tuition fee receipt')
    returning id
), fields as (
    insert into template_field (template_id, position, field_key, label, field_type, required, upload_folder, upload_note)
    select template.id, f.position, f.field_key, f.label, f.field_type, f.required, f.upload_folder, f.upload_note
    from template, (values
        (1, 'school_year', 'School year', 'SCHOOL_YEAR', true, null, null),
        (2, 'payment_proof', 'Payment proof', 'FILE', true, 'tuition-receipts',
            'A screenshot or PDF of the bank transfer')
    ) as f (position, field_key, label, field_type, required, upload_folder, upload_note)
)
insert into template_step (template_id, position, department_code, description, sla_days, requires_attachment)
select template.id, 1, 'FIN', 'Match the payment and issue the receipt', 5, false from template;

-- 3. Official transcript: compiled and stamped by Academic Affairs, then handed over by Finance after the fee.
with template as (
    insert into request_template (title, description, department_code, audience, status, show_processing_time)
    select 'Official transcript', 'Signed and stamped transcript of your grades.',
           'ACAD', 'STUDENT', 'ACTIVE', true
    where not exists (select 1 from request_template where title = 'Official transcript')
    returning id
), fields as (
    insert into template_field (template_id, position, field_key, label, field_type, required, options)
    select template.id, f.position, f.field_key, f.label, f.field_type, f.required, f.options
    from template, (values
        (1, 'copies', 'Number of copies', 'SELECT', true, array['1', '2', '3']::text[]),
        (2, 'language', 'Language', 'SELECT', true, array['Vietnamese', 'English']::text[]),
        (3, 'reason', 'Reason', 'TEXT', false, '{}'::text[])
    ) as f (position, field_key, label, field_type, required, options)
)
insert into template_step (template_id, position, department_code, description, sla_days, requires_attachment)
select template.id, s.position, s.department_code, s.description, s.sla_days, s.requires_attachment
from template, (values
    (1, 'ACAD', 'Compile, sign and stamp the transcript; upload a scan', 3, true),
    (2, 'FIN', 'Collect the fee and hand over the transcript', 1, false)
) as s (position, department_code, description, sla_days, requires_attachment);

-- 4. Teaching schedule change: for lecturers.
with template as (
    insert into request_template (title, description, department_code, audience, status, show_processing_time)
    select 'Teaching schedule change', 'Move or swap a class session.',
           'ACAD', 'LECTURER', 'ACTIVE', false
    where not exists (select 1 from request_template where title = 'Teaching schedule change')
    returning id
), fields as (
    insert into template_field (template_id, position, field_key, label, field_type, required, options)
    select template.id, f.position, f.field_key, f.label, f.field_type, f.required, f.options
    from template, (values
        (1, 'term', 'Term', 'TERM', true, '{}'::text[]),
        (2, 'course_code', 'Course code', 'TEXT', true, '{}'::text[]),
        (3, 'reason', 'Reason', 'TEXTAREA', true, '{}'::text[])
    ) as f (position, field_key, label, field_type, required, options)
)
insert into template_step (template_id, position, department_code, description, sla_days, requires_attachment)
select template.id, 1, 'ACAD', 'Review with the head of department and update the timetable', 3, false
from template;
