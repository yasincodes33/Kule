DROP POLICY IF EXISTS memberships_tenant_isolation ON memberships;

CREATE POLICY memberships_tenant_isolation ON memberships
USING (
    organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    OR invited_email = current_setting('app.current_user_email', true)
    OR user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
);