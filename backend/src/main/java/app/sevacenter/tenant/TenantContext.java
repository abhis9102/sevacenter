package app.sevacenter.tenant;

/**
 * Holds the current request's tenant id in a thread-local, set early in the request
 * (from the Host subdomain or the authenticated user) and read when opening a DB
 * transaction to pin the RLS tenant. Always cleared at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
