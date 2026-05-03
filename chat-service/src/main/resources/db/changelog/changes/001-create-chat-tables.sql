CREATE TABLE messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    sender_username VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_room_id ON messages(room_id);

-- rollback DROP INDEX idx_messages_room_id;
-- rollback DROP TABLE messages;