package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationResponse;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR 0030: on another domain (a staging zone here) every link we hand out and every tenant host
 * follows the configuration. Nothing points at the production domains, which a staging deploy
 * doesn't own; a link there would hand its token to whoever does.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "sevacenter.public.staff-url=https://{slug}.sc.stg.example.test",
        "sevacenter.public.temple-url=https://{slug}.mc.stg.example.test"})
@AutoConfigureMockMvc
class PublicDomainsTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    @Test
    void linksAndTenantHostsFollowTheConfiguredDomains() throws Exception {
        String slug = "dom-" + UUID.randomUUID().toString().substring(0, 8);
        RegistrationResponse r = registration.register(new RegistrationRequest(slug, "Trust " + slug,
                "admin@" + slug + ".example", TestStaff.PASSWORD, "Admin"));
        assertThat(r.adminUrl()).isEqualTo("https://" + slug + ".sc.stg.example.test");
        assertThat(r.publicUrl()).isEqualTo("https://" + slug + ".mc.stg.example.test");

        // Both configured hosts name the trust; the production domains no longer do.
        mvc.perform(host(slug + ".mc.stg.example.test", get("/api/v1/public/temple")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trustName").value("Trust " + slug));
        mvc.perform(host(slug + ".sc.stg.example.test", get("/api/v1/public/temple"))).andExpect(status().isOk());
        mvc.perform(host(slug + ".sevacenter.app", get("/api/v1/public/temple"))).andExpect(status().isNotFound());

        String staff = slug + ".sc.stg.example.test";
        MockHttpSession admin = (MockHttpSession) mvc.perform(host(staff, post("/api/v1/auth/login")).with(csrf())
                        .with(rq -> { rq.setRemoteAddr("10.30.0.1"); return rq; })
                        .param("email", "admin@" + slug + ".example").param("password", TestStaff.PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
        var created = json.readTree(mvc.perform(host(staff, post("/api/v1/users")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", "m@x.example", "displayName", "M",
                                "role", "MEMBER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.at("/setupUrl").asString()).startsWith("https://" + staff + "/setup#token=");
    }

    @Test
    void aResetLinkUsesTheConfiguredDomainWhateverHostTheRequestCameIn() throws Exception {
        String slug = "dom-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(slug, "Trust " + slug, "admin@" + slug + ".example",
                TestStaff.PASSWORD, "Admin"));
        String staff = slug + ".sc.stg.example.test";
        MockHttpSession admin = (MockHttpSession) mvc.perform(host(staff, post("/api/v1/auth/login")).with(csrf())
                        .with(rq -> { rq.setRemoteAddr("10.30.0.2"); return rq; })
                        .param("email", "admin@" + slug + ".example").param("password", TestStaff.PASSWORD))
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
        var created = json.readTree(mvc.perform(host(staff, post("/api/v1/users")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", "r@x.example", "displayName", "R",
                                "role", "MEMBER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = created.at("/setupUrl").asString().split("#token=")[1];
        mvc.perform(host(staff, post("/api/v1/auth/setup")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("token", token, "password", TestStaff.PASSWORD))))
                .andExpect(status().isNoContent());

        // Reset-link poisoning: a forged Host / X-Forwarded-Host never makes it into the link.
        String url = json.readTree(mvc.perform(host(staff, post("/api/v1/users/" + created.at("/user/id").asLong()
                        + "/reset-link")).session(admin).with(csrf())
                        .header("X-Forwarded-Host", "evil.example").header("Forwarded", "host=evil.example"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/resetUrl").asString();
        assertThat(url).startsWith("https://" + staff + "/reset-password#token=").doesNotContain("evil");
    }

    private static MockHttpServletRequestBuilder host(String host, MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(host); return r; });
    }
}
