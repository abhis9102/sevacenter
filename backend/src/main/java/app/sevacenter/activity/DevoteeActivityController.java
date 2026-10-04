package app.sevacenter.activity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import app.sevacenter.auth.StaffUser;
import app.sevacenter.devotee.Devotee;
import app.sevacenter.devotee.DevoteeService;
import app.sevacenter.donation.Donation;
import app.sevacenter.donation.DonationMode;
import app.sevacenter.donation.DonationRepository;
import app.sevacenter.donation.Money;
import app.sevacenter.donation.Receipt;
import app.sevacenter.donation.ReceiptService;
import app.sevacenter.event.Event;
import app.sevacenter.event.EventPass;
import app.sevacenter.event.EventPassRepository;
import app.sevacenter.event.EventRepository;
import app.sevacenter.puja.PujaBookingRepository;
import app.sevacenter.sevak.SevakSignupRepository;
import app.sevacenter.user.ModuleAccess;
import app.sevacenter.user.Role;
import app.sevacenter.user.StaffModule;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything a devotee has done with the temple, on their record (ADR 0023). One read, but each
 * section follows the rules of the module it comes from: donations need LEADER+ and Donations
 * access, sevak offers LEADER+ and Volunteers, pujas and passes their own module. A section the
 * caller may not see is null (not an empty list), so "not allowed" never looks like "none".
 */
@RestController
public class DevoteeActivityController {

    private static final PageRequest TOP = PageRequest.of(0, 200);

    private final DevoteeService devotees;
    private final DonationRepository donations;
    private final ReceiptService receipts;
    private final PujaBookingRepository bookings;
    private final EventPassRepository passes;
    private final EventRepository events;
    private final SevakSignupRepository signups;

    public DevoteeActivityController(DevoteeService devotees, DonationRepository donations, ReceiptService receipts,
                                     PujaBookingRepository bookings, EventPassRepository passes,
                                     EventRepository events, SevakSignupRepository signups) {
        this.devotees = devotees;
        this.donations = donations;
        this.receipts = receipts;
        this.bookings = bookings;
        this.passes = passes;
        this.events = events;
        this.signups = signups;
    }

    @GetMapping("/api/v1/devotees/{id:\\d+}/activity")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public ResponseEntity<Activity> activity(@PathVariable long id, @AuthenticationPrincipal StaffUser staff) {
        Devotee d = devotees.get(id); // 404 for erased, unknown or another trust's devotee (RLS)
        List<String> contacts = new ArrayList<>();
        if (d.getPhone() != null) {
            contacts.add(d.getPhone());
        }
        if (d.getEmail() != null) {
            contacts.add(d.getEmail());
        }
        Activity body = new Activity(id,
                allowed(staff, Role.LEADER, StaffModule.DONATIONS) ? donationsOf(id) : null,
                allowed(staff, Role.MEMBER, StaffModule.PUJAS) ? pujasOf(contacts) : null,
                allowed(staff, Role.MEMBER, StaffModule.EVENTS) ? passesOf(contacts) : null,
                allowed(staff, Role.LEADER, StaffModule.VOLUNTEERS) ? sevaOf(contacts) : null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** Role at least {@code min} (Role is declared most-privileged first) and the module not closed. */
    static boolean allowed(StaffUser staff, Role min, StaffModule module) {
        return staff.role().ordinal() <= min.ordinal() && staff.access(module) != ModuleAccess.NONE;
    }

    private Donations donationsOf(long devoteeId) {
        List<Donation> all = donations.forDevotee(devoteeId, TOP);
        Set<Long> reversed = new HashSet<>();
        long net = 0;
        for (Donation x : all) {
            net += x.getAmountPaise();
            if (x.isReversal()) {
                reversed.add(x.getReversesId());
            }
        }
        List<DonationItem> items = all.stream().filter(x -> !x.isReversal()).map(x -> {
            Optional<Receipt> r = receipts.findByDonationId(x.getId());
            return new DonationItem(x.getId(), x.getReceivedOn(), Money.toRupees(x.getAmountPaise()), x.getMode(),
                    x.getPurpose(), x.getFundId(), reversed.contains(x.getId()), r.map(Receipt::number).orElse(null));
        }).toList();
        return new Donations(Money.toRupees(net), items.size(), items);
    }

    private List<PujaItem> pujasOf(List<String> contacts) {
        return contacts.stream().flatMap(c -> bookings.forContact(c, TOP).stream()).distinct()
                .map(b -> new PujaItem(b.getId(), b.getBookingCode(), b.getPujaName(), b.getPujaDate(),
                        Money.toRupees(b.getAmountPaise()), b.getStatus()))
                .sorted((x, y) -> y.pujaDate().compareTo(x.pujaDate())).toList();
    }

    private List<PassItem> passesOf(List<String> contacts) {
        List<EventPass> mine = contacts.stream().flatMap(c -> passes.forContact(c, TOP).stream()).distinct().toList();
        Map<Long, Event> byId = events.findAllById(mine.stream().map(EventPass::getEventId).distinct().toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        return mine.stream().map(p -> {
            Event e = byId.get(p.getEventId());
            return new PassItem(p.getId(), e == null ? null : e.getTitle(), e == null ? null : e.getStartsAt(),
                    p.getAttendeeCount(), p.getStatus(), p.getCheckedInAt() != null);
        }).toList();
    }

    private List<SevaItem> sevaOf(List<String> contacts) {
        return contacts.stream().flatMap(c -> signups.forContact(c, TOP).stream()).distinct()
                .map(s -> new SevaItem(s.getId(), s.getSevaAreas(), s.getStatus(), s.getCreatedAt())).toList();
    }

    public record Activity(long devoteeId, Donations donations, List<PujaItem> pujaBookings, List<PassItem> eventPasses,
                           List<SevaItem> sevaOffers) { }

    /** Net includes reversals, so a reversed gift counts zero. */
    public record Donations(String net, int count, List<DonationItem> items) { }

    public record DonationItem(long id, LocalDate receivedOn, String amount, DonationMode mode, String purpose,
                               Long fundId, boolean reversed, String receiptNumber) { }

    public record PujaItem(long id, String bookingCode, String pujaName, LocalDate pujaDate, String amount,
                           String status) { }

    public record PassItem(long id, String eventTitle, OffsetDateTime startsAt, int attendeeCount, String status,
                           boolean checkedIn) { }

    public record SevaItem(long id, String sevaAreas, String status, OffsetDateTime createdAt) { }
}
