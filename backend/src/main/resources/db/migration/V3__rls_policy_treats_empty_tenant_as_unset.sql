-- Fix: RLS policy crashed instead of failing closed on reused connections.
--
-- After a transaction that ran set_config('app.tenant_id', <id>, true), Postgres resets the
-- (now defined) custom setting to '' -- not NULL -- for later transactions on the same pooled
-- connection. current_setting('app.tenant_id', true) then returns '', and ''::bigint raises
-- "invalid input syntax for type bigint". Any tenant-less query on such a connection errored
-- (a 500) instead of returning zero rows. Found by TenantIsolationTest.
--
-- nullif(..., '') treats empty as unset, so the comparison is NULL -> no rows: fails closed.
ALTER POLICY app_user_tenant_isolation ON app_user
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint);
