CREATE TABLE project (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    parent_id       UUID        REFERENCES project(id),
    organization_id UUID        NOT NULL,
    owner_id        UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_project_owner_root
    ON project (owner_id)
    WHERE parent_id IS NULL;

