package app.sevacenter;

import static app.sevacenter.TestStaff.admin;
import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.LoginThrottle;
import app.sevacenter.auth.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * A staff account locked by failed logins emails its owner once (ADR 0009), and nobody else: not
 * for unknown emails, not on another trust, not again within the same lock.
 */
@Import({TestcontainersConfiguration.class, LockoutNotificationTest.Mail.class})
@SpringBootTest(properties = "sevacenter.mail.from=no-reply@test.example")
@AutoConfigureMockMvc
class LockoutNotificationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private Mail mail;

    private TestStaff staff;
    private String ip;

    @BeforeEach
    void setUp() {
        staff = new TestStaff(mvc, json, registration);
        mail.sent.clear();
        ip = "10.40." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    @Test
    void theOwnerIsEmailedOnceWhenTheirAccountLocks() throws Exception {
        String a = staff.tenant("lock-a");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES + 2; i++) { // two more inside the same lock
            login(a, admin(a));
        }
        List<SimpleMailMessage> sent = await(1);
        Thread.sleep(300); // nothing else arrives
        assertThat(mail.sent).hasSize(1);
        SimpleMailMessage m = sent.get(0);
        assertThat(m.getTo()).containsExactly(admin(a));
        assertThat(m.getSubject()).contains("locked for 15 minutes");
        assertThat(m.getText()).contains("From IP address: " + ip);
    }

    @Test
    void unknownEmailsAndOtherTrustsGetNothing() throws Exception {
        String a = staff.tenant("lock-b");
        String b = staff.tenant("lock-c");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            login(a, "ghost@" + a + ".example");      // no such account
            login(a, admin(b));                       // a real account, but of another trust
        }
        Thread.sleep(800);
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void theLockIsInTheTrustsAuditLog() throws Exception {
        String a = staff.tenant("lock-d");
        MockHttpSession reader = staff.loginAdmin(a); // signed in before the attack: a lock ends no session
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            login(a, admin(a));
        }
        await(1);
        String log = mvc.perform(on(a, get("/api/v1/audit")).session(reader)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(log).contains("ACCOUNT_LOCKED").contains("ip " + ip);
    }

    private ResultActions login(String slug, String email) throws Exception {
        return mvc.perform(on(slug, post("/api/v1/auth/login")).with(csrf()).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).param("email", email).param("password", "wrong-password-1"));
    }

    private List<SimpleMailMessage> await(int n) throws InterruptedException {
        for (int i = 0; i < 50 && mail.sent.size() < n; i++) {
            Thread.sleep(100);
        }
        assertThat(mail.sent).as("lockout emails").hasSizeGreaterThanOrEqualTo(n);
        return mail.sent;
    }

    /** Records instead of sending. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Mail {
        final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        JavaMailSender recordingMail() {
            return new JavaMailSenderImpl() {
                @Override
                public void send(SimpleMailMessage... messages) {
                    sent.addAll(List.of(messages));
                }
            };
        }
    }
}
