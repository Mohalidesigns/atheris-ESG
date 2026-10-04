-- ============================================================
-- WP-54 (D42): the first platform administrator of a new installation. Tenant creation needs a signed-in caller
-- with tenants:create (auth.create_tenant), so a fresh production database has no way in. This function works only
-- while no platform administrator exists anywhere: it creates the operator's own tenant (settings, built-in roles)
-- and one SUPER_ADMIN user with the given password hash, then returns the user id. Once any platform administrator
-- exists it does nothing and returns NULL. Called at startup by PlatformBootstrap when ESG_BOOTSTRAP_ADMIN_* are set.
-- ============================================================
CREATE FUNCTION auth.bootstrap_platform(p_org TEXT, p_country TEXT, p_currency TEXT, p_email TEXT, p_first TEXT, p_last TEXT,
                                        p_password_hash TEXT) RETURNS UUID
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, auth
  AS $$
DECLARE
  t RECORD;
  new_tenant UUID := gen_random_uuid();
  new_user UUID := gen_random_uuid();
  super_role UUID;
  caller_tenant TEXT := current_setting('app.current_tenant', true);
BEGIN
  -- Row-Level Security applies to the owner too (FORCE), so look tenant by tenant.
  FOR t IN SELECT id FROM auth.tenants LOOP
    PERFORM set_config('app.current_tenant', t.id::text, true);
    IF EXISTS (SELECT 1 FROM auth.user_roles ur JOIN auth.roles r ON r.id = ur.role_id AND r.is_system_role
               JOIN auth.system_roles sr ON sr.name = r.name WHERE sr.scope_type = 'platform') THEN
      PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
      RETURN NULL;
    END IF;
  END LOOP;
  IF EXISTS (SELECT 1 FROM auth.login_identities WHERE email = lower(p_email)) THEN
    RAISE EXCEPTION 'bootstrap: the email is already in use' USING ERRCODE = '23505';
  END IF;
  INSERT INTO auth.tenants (id, name, slug, country_code, subscription_tier, billing_currency, max_users)
  VALUES (new_tenant, p_org, 'platform-operator', p_country, 'enterprise', p_currency, 10);
  PERFORM set_config('app.current_tenant', new_tenant::text, true);
  INSERT INTO auth.tenant_settings (tenant_id, base_currency) VALUES (new_tenant, p_currency);
  PERFORM auth.sync_system_roles(new_tenant);
  INSERT INTO auth.users (id, tenant_id, email, password_hash, first_name, last_name)
  VALUES (new_user, new_tenant, lower(p_email), p_password_hash, p_first, p_last);
  SELECT id INTO super_role FROM auth.roles WHERE tenant_id = new_tenant AND name = 'SUPER_ADMIN';
  INSERT INTO auth.user_roles (tenant_id, user_id, role_id) VALUES (new_tenant, new_user, super_role);
  PERFORM set_config('app.current_tenant', COALESCE(caller_tenant, ''), true);
  RETURN new_user;
END $$;
REVOKE ALL ON FUNCTION auth.bootstrap_platform(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.bootstrap_platform(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO ${app_role};
