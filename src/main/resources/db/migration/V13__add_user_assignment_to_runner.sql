-- V13__add_user_assignment_to_runner.sql

ALTER TABLE agent_connections
ADD COLUMN owner_user_id UUID REFERENCES users(id);

ALTER TABLE tasks
ADD COLUMN assigned_user_id UUID REFERENCES users(id);