package app.sevacenter.tenant;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where each trust lives on the web: its staff app and its MandirCenter site, one URL template per
 * brand (ADR 0030), e.g. {@code https://{slug}.sevacenter.app}. Links we hand out (setup, password
 * reset, registration) are built from this trusted configuration only, never from the request's
 * Host header: that would let anyone who triggers a link point it at their own domain (reset-link
 * poisoning). Tenant host resolution reads its base domains from the same templates.
 *
 * <p>No default on purpose: a deploy that forgets them refuses to start, instead of quietly sending
 * tokens to a domain it doesn't own. https only, except on localhost.
 */
@Component
public class PublicUrls {

    static final String SLUG = "{slug}";

    private final String staffTemplate;
    private final String templeTemplate;
    private final List<String> baseDomains;

    public PublicUrls(@Value("${sevacenter.public.staff-url:}") String staffUrl,
                      @Value("${sevacenter.public.temple-url:}") String templeUrl) {
        this.staffTemplate = validate("sevacenter.public.staff-url (SEVACENTER_STAFF_URL)", staffUrl);
        this.templeTemplate = validate("sevacenter.public.temple-url (SEVACENTER_TEMPLE_URL)", templeUrl);
        this.baseDomains = List.of(baseDomain(staffTemplate), baseDomain(templeTemplate)).stream().distinct().toList();
    }

    /** The trust's staff app, e.g. https://siddheshwar.sevacenter.app (no trailing slash). */
    public String staff(String slug) {
        return staffTemplate.replace(SLUG, slug);
    }

    /** The trust's public MandirCenter site. */
    public String temple(String slug) {
        return templeTemplate.replace(SLUG, slug);
    }

    /** Hosts of the form {@code <slug>.<base>} name a tenant. */
    public List<String> baseDomains() {
        return baseDomains;
    }

    static String validate(String name, String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        if (t.isEmpty()) {
            throw new IllegalStateException(name + " is not set, e.g. https://" + SLUG + ".example.org");
        }
        int at = t.indexOf("://");
        if (at < 0 || t.indexOf(SLUG) != at + 3 || t.indexOf(SLUG, at + 3 + SLUG.length()) >= 0) {
            throw new IllegalStateException(name + " must be scheme://" + SLUG + ".<domain>[:port], with " + SLUG
                    + " once, as the first label");
        }
        URI uri;
        try {
            uri = new URI(t.replace(SLUG, "slug"));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(name + " is not a valid URL");
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !host.startsWith("slug.")
                || host.length() == "slug.".length()) {
            throw new IllegalStateException(name + " must be scheme://" + SLUG + ".<domain>[:port], nothing more");
        }
        boolean local = host.equals("slug.localhost");
        if (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))) {
            throw new IllegalStateException(name + " must use https (http only for localhost)");
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private static String baseDomain(String template) {
        String afterSlug = template.substring(template.indexOf(SLUG) + SLUG.length() + 1);
        int colon = afterSlug.indexOf(':');
        return colon < 0 ? afterSlug : afterSlug.substring(0, colon);
    }
}
