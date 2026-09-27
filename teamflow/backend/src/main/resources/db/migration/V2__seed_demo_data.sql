-- Optional demonstration seed data for local/dev environments, matching
-- the "E-Commerce Platform" scenario in the project's demonstration
-- script. Passwords below are BCrypt hashes of "Password123!".
-- This migration is safe to keep in TEST but should not be relied on
-- as production data.

INSERT INTO users (name, email, password_hash, role, active, created_at, updated_at) VALUES
('Priya Manager',  'pm@teamflow.dev',  '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5cTniogp9YQZKjq8O5V7DQeE.7fCK', 'PROJECT_MANAGER', TRUE, now(), now()),
('Dev Backend',    'backend@teamflow.dev', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5cTniogp9YQZKjq8O5V7DQeE.7fCK', 'TEAM_MEMBER', TRUE, now(), now()),
('Dev Frontend',   'frontend@teamflow.dev', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5cTniogp9YQZKjq8O5V7DQeE.7fCK', 'TEAM_MEMBER', TRUE, now(), now()),
('QA Engineer',    'qa@teamflow.dev', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5cTniogp9YQZKjq8O5V7DQeE.7fCK', 'TEAM_MEMBER', TRUE, now(), now());

INSERT INTO projects (name, description, start_date, end_date, status, manager_id, created_at, updated_at)
VALUES ('E-Commerce Platform', 'Rebuild of the storefront and checkout flow.',
        CURRENT_DATE - INTERVAL '14 days', CURRENT_DATE + INTERVAL '30 days',
        'IN_PROGRESS', (SELECT id FROM users WHERE email = 'pm@teamflow.dev'), now(), now());

INSERT INTO project_members (project_id, user_id, role, joined_at)
SELECT p.id, u.id, u.role, now()
FROM projects p, users u
WHERE p.name = 'E-Commerce Platform';

INSERT INTO tasks (title, description, project_id, owner_id, status, priority, start_date, due_date, progress, created_at, updated_at)
SELECT 'Database Design', 'Design the relational schema.', p.id,
       (SELECT id FROM users WHERE email = 'backend@teamflow.dev'),
       'COMPLETED', 'HIGH', CURRENT_DATE - INTERVAL '14 days', CURRENT_DATE - INTERVAL '10 days', 100, now(), now()
FROM projects p WHERE p.name = 'E-Commerce Platform';

INSERT INTO tasks (title, description, project_id, owner_id, status, priority, start_date, due_date, progress, created_at, updated_at)
SELECT 'Backend API', 'Implement REST endpoints.', p.id,
       (SELECT id FROM users WHERE email = 'backend@teamflow.dev'),
       'IN_PROGRESS', 'CRITICAL', CURRENT_DATE - INTERVAL '9 days', CURRENT_DATE - INTERVAL '1 days', 60, now(), now()
FROM projects p WHERE p.name = 'E-Commerce Platform';

INSERT INTO tasks (title, description, project_id, owner_id, status, priority, start_date, due_date, progress, created_at, updated_at)
SELECT 'Frontend', 'Build the React UI.', p.id,
       (SELECT id FROM users WHERE email = 'frontend@teamflow.dev'),
       'TODO', 'HIGH', CURRENT_DATE, CURRENT_DATE + INTERVAL '10 days', 0, now(), now()
FROM projects p WHERE p.name = 'E-Commerce Platform';

INSERT INTO tasks (title, description, project_id, owner_id, status, priority, start_date, due_date, progress, created_at, updated_at)
SELECT 'Integration Testing', 'End-to-end test the checkout flow.', p.id,
       (SELECT id FROM users WHERE email = 'qa@teamflow.dev'),
       'TODO', 'MEDIUM', CURRENT_DATE + INTERVAL '10 days', CURRENT_DATE + INTERVAL '20 days', 0, now(), now()
FROM projects p WHERE p.name = 'E-Commerce Platform';

INSERT INTO tasks (title, description, project_id, owner_id, status, priority, start_date, due_date, progress, created_at, updated_at)
SELECT 'Deployment', 'Deploy to production.', p.id,
       (SELECT id FROM users WHERE email = 'pm@teamflow.dev'),
       'TODO', 'CRITICAL', CURRENT_DATE + INTERVAL '20 days', CURRENT_DATE + INTERVAL '30 days', 0, now(), now()
FROM projects p WHERE p.name = 'E-Commerce Platform';

-- Dependency chain: Database Design -> Backend API -> Frontend ->
-- Integration Testing -> Deployment
INSERT INTO task_dependencies (task_id, depends_on_task_id, created_at)
SELECT b.id, d.id, now() FROM tasks b, tasks d
WHERE b.title = 'Backend API' AND d.title = 'Database Design';

INSERT INTO task_dependencies (task_id, depends_on_task_id, created_at)
SELECT f.id, b.id, now() FROM tasks f, tasks b
WHERE f.title = 'Frontend' AND b.title = 'Backend API';

INSERT INTO task_dependencies (task_id, depends_on_task_id, created_at)
SELECT t.id, f.id, now() FROM tasks t, tasks f
WHERE t.title = 'Integration Testing' AND f.title = 'Frontend';

INSERT INTO task_dependencies (task_id, depends_on_task_id, created_at)
SELECT dep.id, t.id, now() FROM tasks dep, tasks t
WHERE dep.title = 'Deployment' AND t.title = 'Integration Testing';
