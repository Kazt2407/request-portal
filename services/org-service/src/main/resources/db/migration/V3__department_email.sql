-- Shared mailbox of a department, used for workflow notifications (new work, reminders, overdue steps).
alter table department add column email varchar(320);
alter table department add constraint department_email_format_ck check (email is null or email ~ '^[^@\s]+@[^@\s]+$');
