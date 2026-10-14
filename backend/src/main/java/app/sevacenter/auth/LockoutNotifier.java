package app.sevacenter.auth;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import app.sevacenter.audit.AuditAction;
import app.sevacenter.audit.AuditTrail;
import app.sevacenter.tenant.TenantContext;
import app.sevacenter.tenant.TenantRepository;
import app.sevacenter.user.AppUserRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells a staff member their account was just locked by failed logins (ADR 0009), so a brute-force
 * attempt on them doesn't go unnoticed. Runs off the request thread: the login response must take
 * the same time whether or not the email belongs to a real account, or timing would reveal which
 * emails exist. One email per lock (the throttle reports the lock once), audited as ACCOUNT_LOCKED.
 */
@Component
public class LockoutNotifier {

    private static final Logger log = LoggerFactory.getLogger(LockoutNotifier.class);
    private static final Logger audit = LoggerFactory.getLogger("audit");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'IST'", Locale.ENGLISH);

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final AuditTrail auditTrail;
    private final TransactionTemplate tx;
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    // Small and bounded: a spray across many accounts must not queue unbounded work or mail.
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), r -> {
                Thread t = new Thread(r, "lockout-notifier");
                t.setDaemon(true);
                return t;
            });

    public LockoutNotifier(AppUserRepository users, TenantRepository tenants, AuditTrail auditTrail,
                           TransactionTemplate tx, ObjectProvider<JavaMailSender> mail,
                           @Value("${sevacenter.mail.from:}") String from) {
        this.users = users;
        this.tenants = tenants;
        this.auditTrail = auditTrail;
        this.tx = tx;
        this.mail = mail;
        this.from = from.strip();
    }

    /** Fire and forget; never throws into the login response. */
    public void accountLocked(Long tenantId, String email, String clientIp) {
        if (tenantId == null || tenantId < 0 || email == null || email.isBlank()) {
            return;
        }
        try {
            worker.execute(() -> notify(tenantId, email.strip().toLowerCase(Locale.ROOT), clientIp));
        } catch (RejectedExecutionException e) {
            log.warn("lockout notification dropped: queue full (tenant {})", tenantId);
        }
    }

    private void notify(long tenantId, String email, String clientIp) {
        TenantContext.set(tenantId); // RLS: the lookup and the audit entry stay inside this trust
        try {
            var target = tx.execute(status -> users.findByEmail(email)
                    .filter(u -> u.getDeletedAt() == null)
                    .map(u -> {
                        auditTrail.record(AuditAction.ACCOUNT_LOCKED, "user", u.getId(), "ip " + clientIp);
                        return u;
                    }).orElse(null));
            if (target == null) {
                return; // not a real account here: nothing to tell anyone
            }
            audit.info("event=account_locked tenant={} user={} ip={}", tenantId, target.getId(), clientIp);
            JavaMailSender sender = mail.getIfAvailable();
            if (sender == null || from.isEmpty()) {
                return;
            }
            String trust = tenants.findById(tenantId).map(t -> t.getName()).orElse("SevaCenter");
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(target.getEmail());
            m.setSubject("Your " + trust + " SevaCenter account was locked for 15 minutes");
            m.setText("Someone entered the wrong password for your account " + LoginThrottle.MAX_FAILURES
                    + " times, so it is locked for 15 minutes.\n\n"
                    + "When: " + ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(WHEN) + "\n"
                    + "From IP address: " + clientIp + "\n\n"
                    + "If this was you, wait 15 minutes and try again.\n"
                    + "If it wasn't, someone may be guessing your password: once you can sign in, change it "
                    + "from your profile, and tell your trust's admin.\n");
            sender.send(m);
        } catch (RuntimeException e) {
            log.warn("lockout notification failed (tenant {}): {}", tenantId, e.getClass().getSimpleName());
        } finally {
            TenantContext.clear();
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }
}
