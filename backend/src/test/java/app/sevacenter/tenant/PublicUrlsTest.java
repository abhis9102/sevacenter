package app.sevacenter.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR 0030: the URL templates links are built from are validated at startup; bad config never starts. */
class PublicUrlsTest {

    @Test
    void buildsEachTrustsUrlsAndBaseDomainsFromTheTemplates() {
        PublicUrls urls = new PublicUrls("https://{slug}.sc.stg.example.test/", "https://{slug}.mc.stg.example.test");
        assertThat(urls.staff("siddheshwar")).isEqualTo("https://siddheshwar.sc.stg.example.test");
        assertThat(urls.temple("siddheshwar")).isEqualTo("https://siddheshwar.mc.stg.example.test");
        assertThat(urls.baseDomains()).containsExactly("sc.stg.example.test", "mc.stg.example.test");
    }

    @Test
    void localhostMayUseHttpAndAPort() {
        PublicUrls urls = new PublicUrls("http://{slug}.localhost:3000", "http://{slug}.localhost:3000");
        assertThat(urls.staff("demo")).isEqualTo("http://demo.localhost:3000");
        assertThat(urls.baseDomains()).containsExactly("localhost");
    }

    @Test
    void refusesToStartWithoutThem() {
        assertThatThrownBy(() -> new PublicUrls("", "https://{slug}.mandircenter.app"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SEVACENTER_STAFF_URL) is not set");
        assertThatThrownBy(() -> new PublicUrls("https://{slug}.sevacenter.app", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SEVACENTER_TEMPLE_URL) is not set");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://{slug}.sevacenter.app",            // plain http outside localhost: tokens in clear text
            "https://sevacenter.app",                  // no {slug}
            "https://www.{slug}.sevacenter.app",       // {slug} not the first label
            "https://{slug}.{slug}.app",               // twice
            "https://{slug}.sevacenter.app/app",       // a path
            "https://{slug}.sevacenter.app?x=1",       // a query
            "https://{slug}.sevacenter.app#x",         // a fragment
            "https://user@{slug}.sevacenter.app",      // userinfo
            "https://{slug}.",                         // no domain
            "javascript://{slug}.sevacenter.app",      // another scheme
            "{slug}.sevacenter.app"})                  // no scheme
    void refusesAnythingButSchemeSlugAndDomain(String template) {
        assertThatThrownBy(() -> new PublicUrls(template, "https://{slug}.mandircenter.app"))
                .isInstanceOf(IllegalStateException.class);
    }
}
