package app.sevacenter.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import app.sevacenter.tenant.Tenant;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The scan trust's fixed login code (ADR 0035) can't be switched on where it doesn't belong. */
class ScanLoginCodeTest {

    private final TenantRepository tenants = mock(TenantRepository.class);

    @Test
    void offWhenUnsetInAnyProfile() {
        assertThat(new ScanLoginCode("", "", env(), tenants).enabled()).isFalse();
        assertThat(new ScanLoginCode(null, null, env("staging"), tenants).matches(1, "123456")).isFalse();
    }

    @Test
    void refusesToStartOutsideStagingAndLocal() {
        // No profile is the production baseline; "prod" stands for any future production profile.
        for (MockEnvironment e : new MockEnvironment[] {env(), env("prod"), env("test")}) {
            assertThatThrownBy(() -> new ScanLoginCode("dast-scan", "482913", e, tenants))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("staging only");
            // Half-configured is still configured: never silently ignored in production.
            assertThatThrownBy(() -> new ScanLoginCode("", "482913", e, tenants))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void needsBothATrustAndASixDigitCode() {
        for (String[] bad : new String[][] {{"dast-scan", ""}, {"", "482913"}, {"dast-scan", "12345"},
                {"dast-scan", "abcdef"}, {"dast-scan", "1234567"}}) {
            assertThatThrownBy(() -> new ScanLoginCode(bad[0], bad[1], env("staging"), tenants))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void matchesOnlyTheCodeOnTheScanTrust() {
        when(tenants.findById(1L)).thenReturn(Optional.of(new Tenant("dast-scan", "Scan")));
        when(tenants.findById(2L)).thenReturn(Optional.of(new Tenant("demo", "Demo")));
        ScanLoginCode scan = new ScanLoginCode("dast-scan", "482913", env("staging"), tenants);
        assertThat(scan.matches(1, "482913")).isTrue();
        assertThat(scan.matches(1, "482914")).isFalse();
        assertThat(scan.matches(2, "482913")).isFalse();
        assertThat(scan.matches(3, "482913")).isFalse(); // unknown tenant
    }

    private static MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }
}
