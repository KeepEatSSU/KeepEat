ALTER TABLE push_delivery ADD COLUMN send_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE push_delivery ADD COLUMN next_send_at timestamp with time zone;

CREATE INDEX idx_push_delivery_send_due ON push_delivery USING btree (next_send_at, id) WHERE status = 'PENDING';

CREATE TABLE notification_batch_run (
    run_date date PRIMARY KEY,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone
);
