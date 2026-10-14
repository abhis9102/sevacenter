package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import app.sevacenter.portal.OtpChannel;
import app.sevacenter.portal.ScanLoginCode;
import app.sevacenter.tenant.TenantRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * The DAST scan trust's fixed devotee login code (ADR 0035), through the real login flow: it works
 * only on the scan trust, only in place of a code that was requested, and the attempt cap holds.
 * Switched on here as staging would; ScanLoginCodeTest covers the start-up guard.
 */
@Import({TestcontainersConfiguration.class, DevoteeLoginTest.Senders.class, DevoteeScanLoginTest.Scan.class})
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1", "sevacenter.mail.from=no-reply@test.example"})
@AutoConfigureMockMvc
class DevoteeScanLoginTest {

    static final String SCAN_TRUST = "dast-scan-it";
    static final String SCAN_CODE = "482913";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;

    private TestStaff staff;
    private String ip;

    @BeforeEach
    void setUp() {
        staff = new TestStaff(mvc, json, registration);
        if (tenants.findBySlug(SCAN_TRUST).isEmpty()) {
            registration.register(new RegistrationRequest(SCAN_TRUST, "Scan Trust",
                    TestStaff.admin(SCAN_TRUST), TestStaff.PASSWORD, "Scanner"));
        }
        ip = "10.31." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    @Test
    void theScanCodeLogsInOnTheScanTrustAfterACodeIsRequested() throws Exception {
        String to = "devotee1@dast.example";
        requestCode(SCAN_TRUST, to).andExpect(status().isAccepted());
        Cookie c = new Cookie("SC_DEVOTEE", verify(SCAN_TRUST, to, SCAN_CODE).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SC_DEVOTEE").getValue());
        mvc.perform(on(SCAN_TRUST, get("/api/v1/portal/me")).cookie(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(to));
    }

    @Test
    void theScanCodeAloneOpensNothing() throws Exception {
        verify(SCAN_TRUST, "nocode@dast.example", SCAN_CODE).andExpect(status().isBadRequest());
    }

    @Test
    void theScanCodeFailsOnEveryOtherTrust() throws Exception {
        String other = staff.tenant("dl-real");
        String to = "lakshmi@" + other + ".example";
        requestCode(other, to).andExpect(status().isAccepted());
        verify(other, to, SCAN_CODE).andExpect(status().isBadRequest());
    }

    @Test
    void theAttemptCapStillHoldsOnTheScanTrust() throws Exception {
        String to = "guesser@dast.example";
        requestCode(SCAN_TRUST, to).andExpect(status().isAccepted());
        for (int i = 0; i < 5; i++) {
            verify(SCAN_TRUST, to, "000000".equals(SCAN_CODE) ? "000001" : "000000").andExpect(status().isBadRequest());
        }
        verify(SCAN_TRUST, to, SCAN_CODE).andExpect(status().isBadRequest());
    }

    @Test
    void aScanCodeLoginIsSingleUseLikeAnyCode() throws Exception {
        String to = "once@dast.example";
        requestCode(SCAN_TRUST, to).andExpect(status().isAccepted());
        verify(SCAN_TRUST, to, SCAN_CODE).andExpect(status().isOk());
        verify(SCAN_TRUST, to, SCAN_CODE).andExpect(status().isBadRequest());
    }

    private ResultActions requestCode(String slug, String contact) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/public/devotee-login/code")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("channel", OtpChannel.EMAIL.name(), "contact", contact)));
    }

    private ResultActions verify(String slug, String contact, String code) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/public/devotee-login/verify")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("channel", OtpChannel.EMAIL.name(), "contact", contact, "code", code)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor fromIp() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    /** The scan code switched on the way staging does it. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Scan {
        @Bean
        @Primary
        ScanLoginCode stagingScanCode(TenantRepository tenants) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("staging");
            return new ScanLoginCode(SCAN_TRUST, SCAN_CODE, env, tenants);
        }
    }
}
