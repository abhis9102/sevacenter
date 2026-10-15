package app.sevacenter.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import app.sevacenter.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * A fixed devotee login code for the DAST scan trust on staging (ADR 0035), so the scanner can sign
 * in to the devotee portal without reading an inbox. Fenced like a test account, not a backdoor:
 * <ul>
 *   <li>off unless both {@code SEVACENTER_SCAN_TRUST} and {@code SEVACENTER_SCAN_OTP} are set; no
 *       default code exists anywhere. {@code SEVACENTER_SCAN_TRUST=*} means every trust: staging's
 *       testers can sign in as any devotee there (test data only), since staging can't deliver
 *       real codes;</li>
 *   <li>the app refuses to start with it set outside the staging/local profiles (relaxed binding
 *       means an env var could set it in production);</li>
 *   <li>accepted only on the scan trust, and only in place of a code that was really requested, so
 *       the send limits and the per-code attempt cap still apply;</li>
 *   <li>every use is audited (DevoteeLoginService).</li>
 * </ul>
 */
@Component
public class ScanLoginCode {

    static final String ALLOWED_PROFILES = "staging | local";
    /** Every trust (staging testers), instead of one scan trust. */
    static final String ALL_TRUSTS = "*";

    private final String trust;
    private final byte[] code;
    private final TenantRepository tenants;

    public ScanLoginCode(@Value("${sevacenter.scan-login.trust:}") String trust,
                         @Value("${sevacenter.scan-login.code:}") String code,
                         Environment env, TenantRepository tenants) {
        String t = trust == null ? "" : trust.strip();
        String c = code == null ? "" : code.strip();
        if (!t.isEmpty() || !c.isEmpty()) {
            if (!env.acceptsProfiles(Profiles.of(ALLOWED_PROFILES))) {
                throw new IllegalStateException("sevacenter.scan-login is for staging only: unset "
                        + "SEVACENTER_SCAN_TRUST and SEVACENTER_SCAN_OTP");
            }
            if (t.isEmpty() || !c.matches("\\d{6}")) {
                throw new IllegalStateException("sevacenter.scan-login needs a trust slug and a 6-digit code");
            }
        }
        this.trust = t;
        this.code = c.getBytes(StandardCharsets.US_ASCII);
        this.tenants = tenants;
    }

    public boolean enabled() {
        return !trust.isEmpty();
    }

    /** True only for the configured code on the scan trust. Constant-time on the code. */
    boolean matches(long tenantId, String candidate) {
        if (!enabled() || !MessageDigest.isEqual(code, candidate.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        return tenants.findById(tenantId).map(t -> ALL_TRUSTS.equals(trust) || trust.equals(t.getSlug())).orElse(false);
    }
}
