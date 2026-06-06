ALTER TABLE users
ADD COLUMN primary_provider VARCHAR(50) DEFAULT 'LOCAL',
ADD COLUMN provider_id VARCHAR(255),
ADD COLUMN avatar_url VARCHAR(1000),
ADD COLUMN is_oauth2_linked BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_users_provider_id ON users(provider_id);
