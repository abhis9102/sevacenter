package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** The temple photo and About/History sections, through the real endpoints. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TempleImageTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("img-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aLeaderUploadsAndThePublicPageServesOurJpeg() throws Exception {
        String version = json.readTree(mvc.perform(upload(a, png(1200, 800))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/imageVersion").asString();
        assertThat(version).matches("[0-9a-f]{16}");

        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.imageVersion").value(version));
        byte[] served = mvc.perform(on(a, get("/api/v1/public/temple/image").param("v", version)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).startsWith((byte) 0xFF, (byte) 0xD8);
        // The bare URL must not be cached for a month: it would keep showing an old photo.
        mvc.perform(on(a, get("/api/v1/public/temple/image")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));

        mvc.perform(on(a, delete("/api/v1/temple/image")).session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/public/temple/image"))).andExpect(status().isNotFound());
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.imageVersion").isEmpty());
    }

    @Test
    void oneTemplesPhotoNeverShowsOnAnother() throws Exception {
        mvc.perform(upload(a, png(400, 300))).andExpect(status().isOk());
        String b = staff.tenant("img-b");
        mvc.perform(on(b, get("/api/v1/public/temple/image"))).andExpect(status().isNotFound());
        mvc.perform(on(b, get("/api/v1/public/temple"))).andExpect(jsonPath("$.imageVersion").isEmpty());
    }

    @Test
    void onlyLeadersCanChangeThePhoto() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/temple/image")
                .file(new MockMultipartFile("image", "t.png", "image/png", png(100, 100)))
                .with(host(a)).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/temple/image")
                .file(new MockMultipartFile("image", "t.png", "image/png", png(100, 100)))
                .with(host(a)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(on(a, delete("/api/v1/temple/image")).session(member).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void aFileThatIsNotAPngOrJpegIsRefused() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes();
        mvc.perform(upload(a, svg)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.image").exists());
    }

    @Test
    void aboutAndHistoryArePublishedAsPlainText() throws Exception {
        String about = "A <b>300-year-old</b> Ganesh temple.\nOpen to all.";
        mvc.perform(on(a, put("/api/v1/temple")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("about", about, "history", "Built in 1720.")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.about").value(about));
        mvc.perform(on(a, get("/api/v1/public/temple")))
                .andExpect(jsonPath("$.about").value(about)) // stored as typed; the page renders it as text
                .andExpect(jsonPath("$.history").value("Built in 1720."));
        mvc.perform(on(a, put("/api/v1/temple")).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("about", "x".repeat(4001))))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartHttpServletRequestBuilder upload(String slug, byte[] bytes) {
        return (MockMultipartHttpServletRequestBuilder) multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/temple/image")
                .file(new MockMultipartFile("image", "temple.png", "image/png", bytes))
                .with(host(slug)).session(admin).with(csrf());
    }

    /** Same as TestStaff.on(), for the multipart builder. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor host(String slug) {
        return r -> {
            r.setServerName(slug + ".sevacenter.app");
            return r;
        };
    }

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(220, 120, 30));
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
