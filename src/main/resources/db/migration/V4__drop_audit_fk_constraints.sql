-- Phase 3 Fix: Remove FK constraints from audit_logs
-- =====================================================
-- Audit logs should be independent of referential integrity.
-- The async AuditService may write records before the main transaction commits,
-- and audit logs for failed actions may have null tenant_id/user_id.

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_tenant_id_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_user_id_fkey;
