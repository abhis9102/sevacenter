package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mock.web.MockMultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(100);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private JsonMapper json;

    private String slug;
    private String ip;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        ip = "10.9." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
        slug = "prof-" + UUID.randomUUID().toString().substring(0, 8);
        registration.register(new RegistrationRequest(slug, "Trust " + slug, "admin@" + slug + ".example", PASSWORD, "Original Admin"));
        session = login(slug, "admin@" + slug + ".example", PASSWORD);
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mvc.perform(on(slug, get("/api/v1/profile")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfileReturnsCurrentUserInfoAndDefaultPreferences() throws Exception {
        mvc.perform(on(slug, get("/api/v1/profile")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@" + slug + ".example"))
                .andExpect(jsonPath("$.displayName").value("Original Admin"))
                .andExpect(jsonPath("$.role").value("TRUST_ADMIN"))
                .andExpect(jsonPath("$.tenant").value(slug))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.notifyDevotees").value(true))
                .andExpect(jsonPath("$.notifyDonations").value(true))
                .andExpect(jsonPath("$.notifySecurity").value(true))
                // Review finding: no "opt out of the activity log" (staff can't switch off audit).
                .andExpect(jsonPath("$.privacyActivityLog").doesNotExist())
                .andExpect(jsonPath("$.privacyShowInStaffDirectory").doesNotExist())
                .andExpect(jsonPath("$.hasAvatar").value(false));
    }

    @Test
    void updateProfileUpdatesDisplayNameAndPreferences() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("displayName", "Updated Admin Name");
        req.put("notifyDevotees", false);
        req.put("notifyDonations", false);
        req.put("notifySecurity", true);

        mvc.perform(on(slug, patch("/api/v1/profile")).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Admin Name"))
                .andExpect(jsonPath("$.notifyDevotees").value(false))
                .andExpect(jsonPath("$.notifyDonations").value(false))
                .andExpect(jsonPath("$.notifySecurity").value(true));

        // GET /me also immediately reflects the new display name
        mvc.perform(on(slug, get("/api/v1/me")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Admin Name"));
    }

    @Test
    void changePasswordSuccessAllowsLoginWithNewPassword() throws Exception {
        String newPassword = "new-super-secure-password-123";
        Map<String, String> req = Map.of(
                "currentPassword", PASSWORD,
                "newPassword", newPassword
        );

        mvc.perform(on(slug, post("/api/v1/profile/change-password")).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        // Old password now fails
        mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .param("email", "admin@" + slug + ".example").param("password", PASSWORD))
                .andExpect(status().isUnauthorized());

        // New password succeeds
        mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .param("email", "admin@" + slug + ".example").param("password", newPassword))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordFails() throws Exception {
        Map<String, String> req = Map.of(
                "currentPassword", "wrong-password-here",
                "newPassword", "new-super-secure-password-123"
        );

        mvc.perform(on(slug, post("/api/v1/profile/change-password")).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.currentPassword").exists());
    }

    @Test
    void changePasswordWithTooShortNewPasswordFails() throws Exception {
        Map<String, String> req = Map.of(
                "currentPassword", PASSWORD,
                "newPassword", "short"
        );

        mvc.perform(on(slug, post("/api/v1/profile/change-password")).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void changePasswordWithIdenticalNewPasswordFails() throws Exception {
        Map<String, String> req = Map.of(
                "currentPassword", PASSWORD,
                "newPassword", PASSWORD
        );

        mvc.perform(on(slug, post("/api/v1/profile/change-password")).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.newPassword").value("New password must be different from current password"));
    }

    @Test
    void getAvatarWhenNoneUploadedReturns404() throws Exception {
        mvc.perform(on(slug, get("/api/v1/profile/avatar")).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("avatar_not_found"));
    }

    @Test
    void uploadAvatarValidPngSucceedsAndCanBeRetrieved() throws Exception {
        byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);

        mvc.perform(on(slug, multipart("/api/v1/profile/avatar").file(file)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(true));

        // GET avatar returns image bytes and proper headers
        mvc.perform(on(slug, get("/api/v1/profile/avatar")).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(pngBytes));

        // GET /me reflects hasAvatar
        mvc.perform(on(slug, get("/api/v1/me")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(true));
    }

    @Test
    void uploadAvatarRejectsSvgStoredXss() throws Exception {
        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.svg", "image/svg+xml", svgBytes);

        mvc.perform(on(slug, multipart("/api/v1/profile/avatar").file(file)).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.avatar").value("Only PNG, JPEG, and WebP images are allowed"));
    }

    @Test
    void uploadAvatarRejectsFileOver2MB() throws Exception {
        byte[] hugeBytes = new byte[2 * 1024 * 1024 + 1];
        // give it PNG magic bytes so it reaches the size check
        hugeBytes[0] = (byte) 0x89;
        hugeBytes[1] = 0x50;
        hugeBytes[2] = 0x4E;
        hugeBytes[3] = 0x47;
        hugeBytes[4] = 0x0D;
        hugeBytes[5] = 0x0A;
        hugeBytes[6] = 0x1A;
        hugeBytes[7] = 0x0A;

        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", hugeBytes);

        mvc.perform(on(slug, multipart("/api/v1/profile/avatar").file(file)).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.avatar").value("Avatar image must be 2 MB or less"));
    }

    @Test
    void removeAvatarDeletesPhotoAndReturns404OnSubsequentGet() throws Exception {
        byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 5, 6, 7, 8 };
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);

        mvc.perform(on(slug, multipart("/api/v1/profile/avatar").file(file)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(true));

        // DELETE avatar
        mvc.perform(on(slug, delete("/api/v1/profile/avatar")).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // GET avatar is now 404
        mvc.perform(on(slug, get("/api/v1/profile/avatar")).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("avatar_not_found"));

        // GET /profile reflects hasAvatar == false
        mvc.perform(on(slug, get("/api/v1/profile")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(false));

        // GET /me reflects hasAvatar == false
        mvc.perform(on(slug, get("/api/v1/me")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAvatar").value(false));
    }

    private MockHttpSession login(String slug, String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf())
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .param("email", email).param("password", password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
    }

    private static MockHttpServletRequestBuilder on(String slug, MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder on(
            String slug, org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request) {
        return request.with(r -> { r.setServerName(slug + ".sevacenter.app"); return r; });
    }

    /** Review finding: change-password had no throttle, so a stolen session could guess forever. */
    @Test
    void wrongCurrentPasswordsLockLikeLogin() throws Exception {
        for (int i = 0; i < app.sevacenter.auth.LoginThrottle.MAX_FAILURES; i++) {
            changePassword("wrong-password-" + i).andExpect(status().isBadRequest());
        }
        changePassword(PASSWORD).andExpect(status().isTooManyRequests());
    }

    /**
     * Review finding: the avatar bytes (up to 2 MB) were a field of the user entity, which the
     * session filter loads on every request. They live on UserAvatar only.
     */
    @Test
    void theUserEntityNeverCarriesImageBytes() {
        for (java.lang.reflect.Field f : app.sevacenter.user.AppUser.class.getDeclaredFields()) {
            assertThat(f.getType()).as(f.getName()).isNotEqualTo(byte[].class);
        }
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String current) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("currentPassword", current);
        req.put("newPassword", "a-completely-new-password-1");
        return mvc.perform(on(slug, post("/api/v1/profile/change-password")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)));
    }
}
