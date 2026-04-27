CREATE TABLE user_projection
(
    user_id         UUID PRIMARY KEY,
    organization_id UUID,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    email           VARCHAR(320)
);

create index idx_user_projection_organization_id ON user_projection (organization_id);

CREATE TABLE project_member
(
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES user_projection (user_id),
    role       VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'CONTRIBUTOR', 'VIEWER')),
    granted_by UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_member_project_id ON project_member (project_id);

