-- Replace every placeholder before execution. Generate the BCrypt hash offline.
-- Do not commit the resulting hash, phone number, or operator identity.
START TRANSACTION;
INSERT INTO users (phone, email, email_verified, password, role, status)
VALUES ('<ADMIN_PHONE>', NULL, TRUE, '<BCRYPT_PASSWORD_HASH>', 'admin', 'active');
SET @admin_user_id = LAST_INSERT_ID();
INSERT INTO admin_profiles (user_id, full_name, position)
VALUES (@admin_user_id, '<ADMIN_FULL_NAME>', 'Administrator');
COMMIT;
