package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import app.sevacenter.auth.RegistrationService;
import app.sevacenter.event.EventRepository;
import app.sevacenter.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Events and registration passes (M4, ADR 0014): roles, publishing, capacity under concurrency,
 * gate check-in, contact privacy, cross-tenant codes, limits.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EventTest {

    private static final OffsetDateTime NEXT_WEEK = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).plusDays(7);
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private EventRepository events;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;
    private MockHttpSession member;
    private String ip;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("ev-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
        member = staff.staff(a, admin, "MEMBER");
        ip = "10.12." + (NEXT_IP.get() / 250) + "." + (NEXT_IP.getAndIncrement() % 250 + 1);
    }

    // --- roles and publishing ----------------------------------------------------------------

    @Test
    void membersSeeEventsLeadersManageThem() throws Exception {
        create(member, NEXT_WEEK, 100).andExpect(status().isForbidden());
        long id = id(create(leader, NEXT_WEEK, 100).andExpect(status().isCreated()));
        mvc.perform(on(a, get("/api/v1/events")).session(member)).andExpect(status().isOk());
        mvc.perform(on(a, post("/api/v1/events/" + id + "/publish")).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/events/" + id + "/passes")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, post("/api/v1/events/" + id + "/publish")).session(leader).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void onlyPublishedEventsArePublicAndTakeRegistrations() throws Exception {
        long draft = id(create(leader, NEXT_WEEK, 100));
        long published = publish(id(create(leader, NEXT_WEEK, 100)));
        long cancelled = publish(id(create(leader, NEXT_WEEK, 100)));
        mvc.perform(on(a, post("/api/v1/events/" + cancelled + "/cancel")).session(leader).with(csrf())).andExpect(status().isOk());

        String list = mvc.perform(on(a, get("/api/v1/public/events"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(list).size()).isEqualTo(1);
        assertThat(json.readTree(list).get(0).at("/id").asLong()).isEqualTo(published);
        register(draft, "Lakshmi", 1).andExpect(status().isNotFound());
        register(cancelled, "Lakshmi", 1).andExpect(status().isNotFound());
        register(published, "Lakshmi", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.passCode").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{10}")));
    }

    @Test
    void registrationClosesWhenTheEventStartsOrWhenStaffCloseIt() throws Exception {
        long started = publish(id(create(leader, OffsetDateTime.now().minusHours(1), 100)));
        register(started, "Lakshmi", 1).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("registration_closed"));
        long closed = publish(id(mvc.perform(on(a, post("/api/v1/events")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(eventBody(NEXT_WEEK, 100, false)))));
        register(closed, "Lakshmi", 1).andExpect(status().isConflict());
    }

    /** DAST found "10" accepted as a start time (epoch seconds, i.e. 1970). Only plausible dates. */
    @Test
    void implausibleEventTimesAreRejected() throws Exception {
        mvc.perform(on(a, post("/api/v1/events")).session(leader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("title", "Epoch", "startsAt", "10", "endsAt", "20", "capacity", 10, "registrationOpen", true)))
                .andExpect(status().isBadRequest());
        create(leader, OffsetDateTime.now().plusYears(6), 10).andExpect(status().isBadRequest());
    }

    // --- capacity ----------------------------------------------------------------------------

    @Test
    void capacityIsNeverExceeded() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 3)));
        register(id, "One", 2).andExpect(status().isCreated());
        register(id, "Two", 2).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("event_full"));
        register(id, "Three", 1).andExpect(status().isCreated());
        mvc.perform(on(a, get("/api/v1/public/events"))).andExpect(jsonPath("$[0].placesLeft").value(0));
    }

    /** Registration counts seats under the event row lock: simultaneous registrations serialize. */
    @Test
    void seatsAreCountedUnderARowLock() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 3)));
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(st -> {
                pin(tenantA);
                assertThat(events.lockById(id)).isPresent();
                locked.countDown();
                await(release);
            }));
            locked.await();
            assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
                pin(tenantA);
                jdbc.execute("set local lock_timeout = '300ms'");
                events.lockById(id);
            })).isInstanceOf(CannotAcquireLockException.class);
            release.countDown();
            holder.get();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // --- registration input ------------------------------------------------------------------

    @Test
    void aRegistrationNeedsANameAContactAndASensibleHeadCount() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 100)));
        register(id, "", 1).andExpect(status().isBadRequest());
        register(id, "Lakshmi", 0).andExpect(status().isBadRequest());
        register(id, "Lakshmi", 11).andExpect(status().isBadRequest());
        mvc.perform(on(a, post("/api/v1/public/events/" + id + "/register")).with(csrf()).with(fromIp())
                        .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "No Contact", "count", 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.phone").exists());
    }

    /** Server-owned fields in the body are ignored: a registration can't arrive checked in. */
    @Test
    void aRegistrationCantCheckItselfIn() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 100)));
        String code = code(mvc.perform(on(a, post("/api/v1/public/events/" + id + "/register")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "Sneaky", "count", 1, "phone", "9876543210",
                        "checkedInAt", "2030-01-01T00:00:00Z", "status", "ACTIVE", "passCode", "AAAAAAAAAA"))));
        assertThat(code).isNotEqualTo("AAAAAAAAAA");
        checkIn(member, id, code).andExpect(status().isOk());
    }

    // --- gate check-in -----------------------------------------------------------------------

    @Test
    void aPassChecksInOnceAndTheGateSeesNoContactDetails() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 100)));
        String code = code(register(id, "Lakshmi Iyer", 3));
        String body = checkIn(member, id, code.toLowerCase().substring(0, 5) + "-" + code.substring(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeName").value("Lakshmi Iyer"))
                .andExpect(jsonPath("$.attendeeCount").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("9876543210", "phone", "email");
        checkIn(member, id, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already_checked_in"))
                .andExpect(jsonPath("$.checkedInAt").exists());
    }

    @Test
    void wrongEventCancelledPassesAndGarbageAreRefused() throws Exception {
        long one = publish(id(create(leader, NEXT_WEEK, 100)));
        long other = publish(id(create(leader, NEXT_WEEK, 100)));
        String code = code(register(one, "Lakshmi", 1));
        checkIn(member, other, code).andExpect(status().isNotFound());
        checkIn(member, one, "not-a-code!").andExpect(status().isNotFound());

        String cancelled = code(register(one, "Ravi", 1));
        long passId = json.readTree(mvc.perform(on(a, get("/api/v1/events/" + one + "/passes")).session(leader))
                .andExpect(jsonPath("$[1].phone").value("+919876543210"))
                .andReturn().getResponse().getContentAsString()).get(1).at("/id").asLong();
        mvc.perform(on(a, post("/api/v1/events/" + one + "/passes/" + passId + "/cancel")).session(leader).with(csrf()))
                .andExpect(status().isOk());
        checkIn(member, one, cancelled).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("pass_cancelled"));
    }

    // --- tenants, limits, database -----------------------------------------------------------

    @Test
    void passCodesAndEventIdsOnlyWorkOnTheirOwnTrustsHost() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 100)));
        String code = code(register(id, "Lakshmi", 1));
        String b = staff.tenant("ev-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        mvc.perform(on(b, post("/api/v1/events/" + id + "/check-in")).session(adminB).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("passCode", code))).andExpect(status().isNotFound());
        mvc.perform(on(b, post("/api/v1/public/events/" + id + "/register")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", "X", "count", 1, "phone", "9876543210")))
                .andExpect(status().isNotFound());
        mvc.perform(on(b, get("/api/v1/public/events"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registrationsAreRateLimitedPerClient() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 1000)));
        for (int i = 0; i < 20; i++) {
            register(id, "Visitor " + i, 1).andExpect(status().isCreated());
        }
        register(id, "One too many", 1).andExpect(status().isTooManyRequests());
    }

    @Test
    void eventsAndPassesAreNeverDeletedOnlyCancelled() throws Exception {
        long id = publish(id(create(leader, NEXT_WEEK, 100)));
        register(id, "Lakshmi", 1);
        long tenantA = tenants.findBySlug(a).orElseThrow().getId();
        assertThatThrownBy(() -> tx.executeWithoutResult(st -> {
            pin(tenantA);
            jdbc.update("delete from event_pass where event_id = ?", id);
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    // --- helpers -----------------------------------------------------------------------------

    // --- passes issued at the counter or gate (ADR 0026) ------------------------------------------

    @Test
    void staffIssueAWalkInPassAfterOnlineRegistrationClosesAndSeatsStillCount() throws Exception {
        long id = publish(id(mvc.perform(on(a, post("/api/v1/events")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(eventBody(NEXT_WEEK, 3, false)))));
        register(id, "Online devotee", 1).andExpect(status().isConflict());
        String code = code(issue(member, id, "Walk-in family", 2));
        checkIn(member, id, code).andExpect(status().isOk());
        issue(member, id, "One too many", 2).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("event_full"));
        issue(member, id, "Last place", 1).andExpect(status().isCreated());
    }

    @Test
    void aGatePassNeedsAPublishedEvent() throws Exception {
        long draft = id(create(leader, NEXT_WEEK, 100));
        issue(member, draft, "Walk-in", 1).andExpect(status().isNotFound());
        mvc.perform(on(a, post("/api/v1/events/" + draft + "/passes")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(staff.body("name", "Anonymous", "count", 1))).andExpect(status().isUnauthorized());
    }

    private ResultActions issue(MockHttpSession session, long eventId, String name, int count) throws Exception {
        return mvc.perform(on(a, post("/api/v1/events/" + eventId + "/passes")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", name, "count", count)));
    }

    private String eventBody(OffsetDateTime start, int capacity, boolean open) {
        return staff.body("title", "Janmashtami Darshan", "description", "Midnight darshan", "startsAt", start.toString(),
                "endsAt", start.plusHours(3).toString(), "capacity", capacity, "registrationOpen", open);
    }

    private ResultActions create(MockHttpSession session, OffsetDateTime start, int capacity) throws Exception {
        return mvc.perform(on(a, post("/api/v1/events")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(eventBody(start, capacity, true)));
    }

    private long publish(long id) throws Exception {
        mvc.perform(on(a, post("/api/v1/events/" + id + "/publish")).session(leader).with(csrf())).andExpect(status().isOk());
        return id;
    }

    private ResultActions register(long eventId, String name, int count) throws Exception {
        return mvc.perform(on(a, post("/api/v1/public/events/" + eventId + "/register")).with(csrf()).with(fromIp())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("name", name, "count", count, "phone", "98765 43210")));
    }

    private ResultActions checkIn(MockHttpSession session, long eventId, String code) throws Exception {
        return mvc.perform(on(a, post("/api/v1/events/" + eventId + "/check-in")).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(staff.body("passCode", code)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor fromIp() {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    private long id(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).at("/id").asLong();
    }

    private String code(ResultActions registered) throws Exception {
        return json.readTree(registered.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/passCode").asString();
    }

    private void pin(long tenantId) {
        jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, Long.toString(tenantId));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
