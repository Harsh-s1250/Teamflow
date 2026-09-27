-- TeamFlow initial schema.
-- Versioned via Flyway so schema evolution is reproducible across
-- LOCAL / TEST / PRODUCTION rather than relying on Hibernate ddl-auto.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('PROJECT_MANAGER', 'TEAM_MEMBER'))
);

CREATE TABLE projects (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(30) NOT NULL,
    manager_id      BIGINT NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_projects_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    CONSTRAINT chk_projects_status CHECK (status IN ('PLANNED','IN_PROGRESS','ON_HOLD','COMPLETED','CANCELLED')),
    CONSTRAINT chk_projects_dates CHECK (start_date <= end_date)
);

CREATE INDEX idx_projects_manager_id ON projects (manager_id);
CREATE INDEX idx_projects_status ON projects (status);

CREATE TABLE project_members (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    role            VARCHAR(30) NOT NULL,
    joined_at       TIMESTAMP NOT NULL,
    CONSTRAINT fk_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- Core integrity rule: a user cannot be added twice to the same project.
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX idx_members_user_id ON project_members (user_id);

CREATE TABLE tasks (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(4000),
    project_id      BIGINT NOT NULL,
    owner_id        BIGINT,
    status          VARCHAR(30) NOT NULL,
    priority        VARCHAR(30) NOT NULL,
    start_date      DATE,
    due_date        DATE,
    progress        INTEGER NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_tasks_status CHECK (status IN ('TODO','IN_PROGRESS','BLOCKED','COMPLETED')),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_tasks_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT chk_tasks_dates CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date),
    -- A COMPLETED task must be at 100% progress. Enforced at the database
    -- level in addition to the service layer, per the project's integrity
    -- requirements.
    CONSTRAINT chk_tasks_completed_progress CHECK (status <> 'COMPLETED' OR progress = 100)
);

CREATE INDEX idx_tasks_project_id ON tasks (project_id);
CREATE INDEX idx_tasks_owner_id ON tasks (owner_id);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);

CREATE TABLE task_dependencies (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL,
    depends_on_task_id  BIGINT NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_dep_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_depends_on FOREIGN KEY (depends_on_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    -- A task cannot depend on itself.
    CONSTRAINT chk_dep_not_self CHECK (task_id <> depends_on_task_id),
    -- Duplicate dependencies are forbidden.
    CONSTRAINT uk_dep_task_dependson UNIQUE (task_id, depends_on_task_id)
);

CREATE INDEX idx_dep_task_id ON task_dependencies (task_id);
CREATE INDEX idx_dep_depends_on_task_id ON task_dependencies (depends_on_task_id);
