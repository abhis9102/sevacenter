package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import app.sevacenter.auth.RegistrationRequest;
import app.sevacenter.auth.RegistrationService;
import app.sevacenter.auth.SlugAlreadyTakenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Concurrent duplicates both pass an "exists?" check; the unique index lets one win. The loser must
 * get the same clean conflict as a plain duplicate, never a 500 (the race DAST found on funds).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DuplicateRaceTest {

    private static final int N = 8;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    @Test
    void concurrentInvitesOfOneEmailGiveOneUserAndCleanConflicts() throws Exception {
        TestStaff staff = new TestStaff(mvc, json, registration);
        String a = staff.tenant("race-a");
        MockHttpSession admin = staff.loginAdmin(a);
        List<Integer> statuses = race(() -> mvc.perform(on(a, post("/api/v1/users")).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("email", "same@race.example", "displayName", "Same", "role", "MEMBER")))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    }

    @Test
    void concurrentRegistrationsOfOneSlugGiveOneTrust() throws Exception {
        String slug = "race-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> outcomes = race(() -> {
            try {
                registration.register(new RegistrationRequest(slug, "Trust", "admin-" + UUID.randomUUID() + "@race.example",
                        TestStaff.PASSWORD, "Admin"));
                return "created";
            } catch (SlugAlreadyTakenException e) {
                return "taken";
            }
        });
        assertThat(outcomes).containsOnly("created", "taken");
        assertThat(outcomes).filteredOn("created"::equals).hasSize(1);
    }

    private static <T> List<T> race(Callable<T> task) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < N; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
