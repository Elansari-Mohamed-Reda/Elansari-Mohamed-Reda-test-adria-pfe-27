CREATE TABLE bank_movement_lock (id BIGINT PRIMARY KEY);
INSERT INTO bank_movement_lock(id) VALUES (1);
CREATE TABLE bank_movements (
 operation_id UUID PRIMARY KEY,
 fingerprint VARCHAR(512) NOT NULL,
 result_json VARCHAR(4000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
