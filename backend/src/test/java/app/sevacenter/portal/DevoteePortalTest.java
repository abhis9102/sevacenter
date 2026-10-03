package app.sevacenter.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import app.sevacenter.TestcontainersConfiguration;
import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "sevacenter.dev-tokens=true")
@AutoConfigureMockMvc
class DevoteePortalTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private String tenantSlugA;
    private String tenantSlugB;

    @BeforeEach
    void setUp() {
        tenantSlugA = "portal-a-" + UUID.randomUUID().toString().substring(0, 8);
        tenantSlugB = "portal-b-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(tenantSlugA, "Mandir A", "admin@" + tenantSlugA + ".org", "password-123456", "Admin A"));
        registration.register(new RegistrationRequest(tenantSlugB, "Mandir B", "admin@" + tenantSlugB + ".org", "password-123456", "Admin B"));
    }

    private MockHttpServletRequestBuilder onMandir(String slug, MockHttpServletRequestBuilder req) {
        return req.with(r -> { r.setServerName(slug + ".mandircenter.app"); return r; });
    }

    @Test
    void sendOtpAndVerify_newDevotee_requiresRegistrationThenLogsIn() throws Exception {
        String phone = "+919876543210";

        // 1. Request OTP on tenant A's mandircenter domain
        MvcResult sendRes = mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/send-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent successfully"))
                .andExpect(jsonPath("$.devOtp").exists())
                .andReturn();

        JsonNode sendJson = json.readTree(sendRes.getResponse().getContentAsString());
        String otp = sendJson.get("devOtp").asString();

        // 2. Verify OTP -> Not yet registered
        mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/verify-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone, "otp", otp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.verifiedIdentifier").value(phone));

        // 3. Register devotee
        MvcResult regRes = mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/register"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Ramesh Kumar",
                                "phone", phone,
                                "city", "Varanasi",
                                "state", "Uttar Pradesh"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.devotee.fullName").value("Ramesh Kumar"))
                .andReturn();

        Cookie sessionCookie = regRes.getResponse().getCookie(DevoteePortalController.DEVOTEE_COOKIE_NAME);
        assertThat(sessionCookie).isNotNull();

        // 4. Access /api/v1/portal/me
        mvc.perform(onMandir(tenantSlugA, get("/api/v1/portal/me"))
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ramesh Kumar"))
                .andExpect(jsonPath("$.city").value("Varanasi"));

        // 5. Next time: existing devotee verifies OTP and logs in immediately
        MvcResult sendRes2 = mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/send-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone))))
                .andExpect(status().isOk())
                .andReturn();

        String otp2 = json.readTree(sendRes2.getResponse().getContentAsString()).get("devOtp").asString();

        mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/verify-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone, "otp", otp2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.devotee.fullName").value("Ramesh Kumar"));
    }

    @Test
    void onlineDonation_issuesReceiptWithoutPan_andTracksDevoteeHistory() throws Exception {
        // Register a devotee
        String email = "anita@example.org";
        MvcResult regRes = mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/register"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Anita Sharma",
                                "email", email,
                                "city", "Ayodhya"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie sessionCookie = regRes.getResponse().getCookie(DevoteePortalController.DEVOTEE_COOKIE_NAME);

        // Make an online donation of ₹1,100 without PAN card
        mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/donations"))
                        .with(csrf())
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "amountRupees", 1100,
                                "mode", "UPI",
                                "purpose", "Annadanam Seva",
                                "reference", "UPI/REF/999888"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").exists())
                .andExpect(jsonPath("$.amountRupees").value(1100))
                .andExpect(jsonPath("$.purpose").value("Annadanam Seva"))
                .andExpect(jsonPath("$.mandirName").value("Mandir A"));

        // Devotee views donation history
        mvc.perform(onMandir(tenantSlugA, get("/api/v1/portal/donations"))
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amountRupees").value(1100))
                .andExpect(jsonPath("$[0].purpose").value("Annadanam Seva"));
    }

    @Test
    void tenantIsolation_devoteeSessionFromTenantACannotAccessTenantB() throws Exception {
        // Register devotee on Tenant A
        MvcResult regRes = mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/register"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Devotee Tenant A",
                                "phone", "+919111122222"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie sessionCookieA = regRes.getResponse().getCookie(DevoteePortalController.DEVOTEE_COOKIE_NAME);

        // Tenant A session cannot access Tenant B portal
        mvc.perform(onMandir(tenantSlugB, get("/api/v1/portal/me"))
                        .cookie(sessionCookieA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidOtp_rejectedWith400() throws Exception {
        String phone = "+919876543211";

        mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/send-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone))))
                .andExpect(status().isOk());

        // Incorrect OTP
        mvc.perform(onMandir(tenantSlugA, post("/api/v1/portal/auth/verify-otp"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("channel", "PHONE", "identifier", phone, "otp", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_otp"));
    }
}
