package app.sevacenter.auth;

import java.util.Locale;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.user.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Finds staff only within the request's tenant: RLS scopes {@code findByEmail} to the tenant
 * pinned from the Host, so tenant A's login page can never find tenant B's user. No tenant
 * (unknown host) means no user. The same "not found" either way; DaoAuthenticationProvider
 * still runs a dummy password check, so response timing doesn't reveal which.
 */
@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public StaffUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        if (TenantContext.get() == null || email == null) {
            throw new UsernameNotFoundException("not found");
        }
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(StaffUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
