CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    owner_username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE room_members (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(room_id, username),
    CONSTRAINT fk_room FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

CREATE INDEX idx_rooms_owner_username ON rooms(owner_username);

-- rollback DROP INDEX idx_rooms_owner_username;
-- rollback DROP TABLE room_members;
-- rollback DROP TABLE rooms;