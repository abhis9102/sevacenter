package app.sevacenter.auth;

import java.util.Collection;
import java.util.List;

import app.sevacenter.user.AppUser;
import app.sevacenter.user.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated staff member. Carries the tenant it belongs to, so every request can be
 * checked against the Host's tenant ({@link TenantBindingFilter}). Stored in the HTTP session,
 * hence Serializable via UserDetails; the password hash is erased after authentication.
 */
public final class StaffUser implements UserDetails, CredentialsContainer {

    private final long userId;
    private final long tenantId;
    private final String email;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    StaffUser(AppUser user) {
        this.userId = user.getId();
        this.tenantId = user.getTenantId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.active = "ACTIVE".equals(user.getStatus());
        this.passwordHash = user.getPasswordHash();
    }

    public long userId() {
        return userId;
    }

    public long tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
