-- V8__add_audit_log_entries_rls_policy.sql
DROP POLICY IF EXISTS audit_log_entries_tenant_isolation ON audit_log_entries;

CREATE POLICY audit_log_entries_tenant_isolation ON audit_log_entries
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);