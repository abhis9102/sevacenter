package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** About-page photos: re-encoded, per-section cap, leaders only, per trust. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GalleryTest {

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
        a = staff.tenant("gal-a");
        admin = staff.loginAdmin(a);
    }

    @Test
    void aLeaderAddsCaptionedPhotosThatThePublicAboutPageServes() throws Exception {
        String body = upload(a, admin, "HISTORY", "The 1952 renovation", png()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").value("HISTORY")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/id").asLong();
        String v = json.readTree(body).at("/version").asString();
        mvc.perform(on(a, get("/api/v1/public/temple/gallery"))).andExpect(jsonPath("$[0].caption").value("The 1952 renovation"));
        mvc.perform(on(a, get("/api/v1/public/temple"))).andExpect(jsonPath("$.gallery").value(true));
        mvc.perform(on(a, get("/api/v1/public/temple/gallery/" + id).param("v", v))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(on(a, delete("/api/v1/temple/gallery/" + id)).session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(on(a, get("/api/v1/public/temple/gallery/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void eachSectionHoldsAtMostTwelvePhotos() throws Exception {
        byte[] img = png();
        for (int i = 0; i < 12; i++) {
            upload(a, admin, "ABOUT", null, img).andExpect(status().isCreated());
        }
        upload(a, admin, "ABOUT", null, img).andExpect(status().isBadRequest());
        upload(a, admin, "HISTORY", null, img).andExpect(status().isCreated()); // the other section has room
    }

    @Test
    void onlyLeadersUploadAndOnlyRealImagesAreKept() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        upload(a, member, "ABOUT", null, png()).andExpect(status().isForbidden());
        upload(a, admin, "ABOUT", null, "<svg onload=alert(1)/>".getBytes()).andExpect(status().isBadRequest());
    }

    @Test
    void anotherTrustSeesNoneOfThem() throws Exception {
        long id = json.readTree(upload(a, admin, "ABOUT", null, png()).andReturn().getResponse().getContentAsString())
                .at("/id").asLong();
        String b = staff.tenant("gal-b");
        mvc.perform(on(b, get("/api/v1/public/temple/gallery"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(on(b, get("/api/v1/public/temple/gallery/" + id))).andExpect(status().isNotFound());
    }

    private ResultActions upload(String slug, MockHttpSession session, String section, String caption, byte[] bytes)
            throws Exception {
        var r = multipart("/api/v1/temple/gallery").file(new MockMultipartFile("image", "p.png", "image/png", bytes))
                .param("section", section);
        if (caption != null) {
            r.param("caption", caption);
        }
        return mvc.perform(r.with(host(slug)).session(session).with(csrf()));
    }

    private static RequestPostProcessor host(String slug) {
        return req -> {
            req.setServerName(slug + ".sevacenter.app");
            return req;
        };
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(120, 60, 20));
        g.fillRect(0, 0, 300, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
