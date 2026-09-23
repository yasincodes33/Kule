DROP POLICY approval_requests_tenant_isolation ON approval_requests;

CREATE POLICY approval_requests_tenant_isolation ON approval_requests
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);