-- Extend postgres_password column to accommodate encrypted data
-- Encrypted format: base64(IV[12 bytes] + ciphertext + auth_tag[16 bytes])
-- A 32-char password becomes ~80 chars when encrypted
ALTER TABLE clusters ALTER COLUMN postgres_password TYPE VARCHAR(512);
