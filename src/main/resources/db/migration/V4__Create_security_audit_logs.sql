CREATE TABLE IF NOT EXISTS security_audit_logs (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_audit_logs_user_event_time 
ON security_audit_logs (username, event_type, created_at);
