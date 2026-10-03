package app.sevacenter.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The trust's audit log (ADR 0020): TRUST_ADMIN only, read-only, newest first. */
@RestController
public class AuditController {

    private final AuditEntryRepository entries;
    private final AppUserRepository users;

    public AuditController(AuditEntryRepository entries, AppUserRepository users) {
        this.entries = entries;
        this.users = users;
    }

    @GetMapping("/api/v1/audit")
    @PreAuthorize("hasRole('TRUST_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<AuditPage> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          @RequestParam(required = false) AuditAction action) {
        Page<AuditEntry> result = entries.newestFirst(action,
                PageRequest.of(Math.clamp(page, 0, 10_000), Math.clamp(size, 1, 100)));
        Map<Long, AppUser> actors = users.findAllById(result.stream().map(AuditEntry::getActorId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<Item> items = result.stream().map(e -> new Item(e.getId(), e.getCreatedAt(), e.getAction(),
                actorName(e.getActorId(), actors), e.getTargetType(), e.getTargetId(), e.getDetail())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AuditPage(items, result.getNumber(), result.getSize(), result.getTotalElements()));
    }

    private static String actorName(Long actorId, Map<Long, AppUser> actors) {
        if (actorId == null) {
            return "Public / system";
        }
        AppUser u = actors.get(actorId);
        if (u == null) {
            return "Unknown user";
        }
        return u.getDeletedAt() != null ? u.getDisplayName() + " (deleted)" : u.getDisplayName();
    }

    public record Item(long id, OffsetDateTime at, AuditAction action, String actor, String targetType, Long targetId,
                       String detail) { }

    public record AuditPage(List<Item> items, int page, int size, long total) { }
}
