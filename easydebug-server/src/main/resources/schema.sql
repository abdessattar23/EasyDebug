CREATE TABLE IF NOT EXISTS capture_event (
    id VARCHAR(80) PRIMARY KEY,
    kind VARCHAR(64) NOT NULL,
    direction VARCHAR(16),
    service VARCHAR(300),
    hook VARCHAR(80),
    occurred_at TIMESTAMP NOT NULL,
    duration_ms BIGINT,
    method VARCHAR(40),
    url CLOB,
    host VARCHAR(500),
    status INT,
    error CLOB,
    payload CLOB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_capture_occurred ON capture_event (occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_capture_kind_time ON capture_event (kind, occurred_at DESC);
