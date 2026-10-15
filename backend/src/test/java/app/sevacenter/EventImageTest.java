package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

import javax.imageio.ImageIO;

import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Event cover photos: leaders upload, shown publicly only once the event is published. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EventImageTest {

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
        a = staff.tenant("evimg-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void creatingAnEventCreatesExactlyOne() throws Exception {
        event("Diwali");
        mvc.perform(on(a, get("/api/v1/events")).session(admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aDraftsPhotoStaysPrivateUntilTheEventIsPublished() throws Exception {
        long id = event("Kartik Purnima");
        String v = json.readTree(upload(id, admin, png()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).at("/imageVersion").asString();
        mvc.perform(on(a, get("/api/v1/events")).session(admin)).andExpect(jsonPath("$[0].imageVersion").value(v));
        mvc.perform(on(a, get("/api/v1/public/events/" + id + "/image"))).andExpect(status().isNotFound()); // draft

        mvc.perform(on(a, post("/api/v1/events/" + id + "/publish")).session(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(on(a, get("/api/v1/public/events"))).andExpect(jsonPath("$[0].imageVersion").value(v));
        mvc.perform(on(a, get("/api/v1/public/events/" + id + "/image")).param("v", v)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        mvc.perform(on(a, delete("/api/v1/events/" + id + "/image")).session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/public/events/" + id + "/image"))).andExpect(status().isNotFound());
    }

    @Test
    void onlyLeadersUploadRealImagesToTheirOwnTrustsEvents() throws Exception {
        long id = event("Gita Jayanti");
        upload(id, staff.staff(a, admin, "MEMBER"), png()).andExpect(status().isForbidden());
        upload(id, admin, "<svg onload=alert(1)/>".getBytes()).andExpect(status().isBadRequest());
        String b = staff.tenant("evimg-b");
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/events/" + id + "/image")
                .file(new MockMultipartFile("image", "p.png", "image/png", png()))
                .with(host(b)).session(staff.loginAdmin(b)).with(csrf())).andExpect(status().isNotFound());
    }

    private long event(String title) throws Exception {
        OffsetDateTime at = OffsetDateTime.now().plusDays(10);
        return json.readTree(mvc.perform(on(a, post("/api/v1/events")).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staff.body("title", title, "startsAt", at.toString(), "endsAt", at.plusHours(3).toString(),
                                "registrationOpen", true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private ResultActions upload(long id, MockHttpSession session, byte[] bytes) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/events/" + id + "/image")
                .file(new MockMultipartFile("image", "p.png", "image/png", bytes))
                .with(host(a)).session(session).with(csrf()));
    }

    private static RequestPostProcessor host(String slug) {
        return req -> {
            req.setServerName(slug + ".sevacenter.app");
            return req;
        };
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(200, 90, 10));
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
