-- ============================================================
-- LOCAL PROFILE ONLY (spring.flyway.locations in application-local.yml).
-- Sets a known password for the seeded demo users so developers can log in.
-- Replaces config/DataSeeder.java, which reset every user's password on each start (AS-IS S4).
-- Password: LocalDev!2026  (documented in README; never used outside local)
-- WP-13 replaces these 4 users with one seeded user per role (D10).
-- ============================================================
UPDATE users
SET password_hash = '$2a$10$ZttNN/ZvtrBk5LL0kcrmkOyP49KDecsM/iKe42cwb6M6NfDTROJpm',
    password_changed_at = CURRENT_TIMESTAMP
WHERE id IN ('u-001', 'u-002', 'u-003', 'u-004');
