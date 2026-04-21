CREATE TABLE project_member (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id  UUID        NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'CONTRIBUTOR', 'VIEWER')),
    granted_by  UUID        NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_member_project_id ON project_member (project_id);
CREATE INDEX idx_project_member_user_id    ON project_member (user_id);

