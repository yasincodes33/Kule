DROP POLICY IF EXISTS notifications_tenant_isolation ON notifications;

CREATE POLICY notifications_tenant_isolation ON notifications
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);