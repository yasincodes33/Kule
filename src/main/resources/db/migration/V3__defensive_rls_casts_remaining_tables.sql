DROP POLICY IF EXISTS projects_tenant_isolation ON projects;
CREATE POLICY projects_tenant_isolation ON projects
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS agent_connections_tenant_isolation ON agent_connections;
CREATE POLICY agent_connections_tenant_isolation ON agent_connections
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS agent_capabilities_tenant_isolation ON agent_capabilities;
CREATE POLICY agent_capabilities_tenant_isolation ON agent_capabilities
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS tasks_tenant_isolation ON tasks;
CREATE POLICY tasks_tenant_isolation ON tasks
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS task_logs_tenant_isolation ON task_logs;
CREATE POLICY task_logs_tenant_isolation ON task_logs
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS approval_requests_tenant_isolation ON approval_requests;
CREATE POLICY approval_requests_tenant_isolation ON approval_requests
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS audit_log_entries_tenant_isolation ON audit_log_entries;
CREATE POLICY audit_log_entries_tenant_isolation ON audit_log_entries
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS notifications_tenant_isolation ON notifications;
CREATE POLICY notifications_tenant_isolation ON notifications
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS usage_records_tenant_isolation ON usage_records;
CREATE POLICY usage_records_tenant_isolation ON usage_records
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);