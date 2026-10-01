-- Notification templates (WP-15). English first; other locales arrive with WP-45 (professional translation).
-- {{name}} placeholders are filled by NotificationService. Repeatable: edits re-apply.
INSERT INTO notifications.notification_templates (notification_type, channel, locale, subject, body) VALUES
  ('WELCOME', 'in_app', 'en', 'Welcome to Atheris ESG', 'Your account in {{tenantName}} is ready.'),
  ('WELCOME', 'email', 'en', 'Your Atheris ESG account',
   'Hello {{firstName}},' || chr(10) || chr(10) || 'An account has been created for you in {{tenantName}}. Your administrator will give you your initial password.' || chr(10) || chr(10) || 'Atheris ESG'),
  ('ROLES_CHANGED', 'in_app', 'en', 'Your access changed', 'Your roles are now: {{roles}}.'),
  ('ROLES_CHANGED', 'email', 'en', 'Your Atheris ESG access changed',
   'Hello {{firstName}},' || chr(10) || chr(10) || 'Your roles in {{tenantName}} are now: {{roles}}.' || chr(10) || 'If you did not expect this, contact your administrator.' || chr(10) || chr(10) || 'Atheris ESG'),
  ('ROLES_CHANGED', 'sms', 'en', NULL, 'Atheris ESG: your roles in {{tenantName}} changed to {{roles}}.'),
  ('PASSWORD_SET_BY_ADMIN', 'in_app', 'en', 'Your password was reset', 'An administrator set a new password for you. All your sessions were signed out.'),
  ('PASSWORD_SET_BY_ADMIN', 'email', 'en', 'Your Atheris ESG password was reset',
   'Hello {{firstName}},' || chr(10) || chr(10) || 'An administrator in {{tenantName}} set a new password for your account and signed you out everywhere.' || chr(10) || 'If you did not ask for this, contact your administrator immediately.' || chr(10) || chr(10) || 'Atheris ESG'),
  ('PASSWORD_SET_BY_ADMIN', 'sms', 'en', NULL, 'Atheris ESG: your password was reset by an administrator. Contact them if this was not you.'),
  ('DEADLINE_REMINDER', 'in_app', 'en', '{{framework}} filing due in {{days}} days', '{{description}} is due on {{dueDate}}.'),
  ('DEADLINE_REMINDER', 'email', 'en', '{{framework}} filing due in {{days}} days',
   'Hello {{firstName}},' || chr(10) || chr(10) || '{{description}} is due on {{dueDate}} ({{days}} days).' || chr(10) || chr(10) || 'Atheris ESG'),
  ('DEADLINE_REMINDER', 'sms', 'en', NULL, 'Atheris ESG: {{framework}} filing due {{dueDate}} ({{days}} days).')
ON CONFLICT (notification_type, channel, locale) DO UPDATE SET subject = EXCLUDED.subject, body = EXCLUDED.body;
