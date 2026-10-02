package app.sevacenter.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Host-header tenant confusion (threat model): only {@code <slug>.<our base domain>} resolves. */
class TenantHostResolutionTest {

    private static final List<String> BASES = List.of("sevacenter.app", "mandircenter.app");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(nullValues = "none", value = {
            "siddheshwar.sevacenter.app,       siddheshwar",
            "SIDDHESHWAR.MandirCenter.app,     siddheshwar",
            "siddheshwar.attacker.example,     none",   // someone else's domain
            "siddheshwar.sevacenter.app.evil.example, none", // our domain as a prefix
            "a.siddheshwar.sevacenter.app,     none",   // extra labels
            "sevacenter.app,                   none",   // bare base domain
            ".sevacenter.app,                  none",   // empty label
            "evilsevacenter.app,               none",   // suffix without the dot
            "localhost,                        none",
            "none,                             none"})
    void onlyOurBaseDomainsResolveATenant(String host, String expected) {
        assertThat(TenantResolutionFilter.slugFromHost(host, BASES)).isEqualTo(expected);
    }
}
