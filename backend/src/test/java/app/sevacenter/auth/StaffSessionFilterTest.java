package app.sevacenter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import app.sevacenter.tenant.TenantContext;
import app.sevacenter.user.AppUser;
import app.sevacenter.user.AppUserRepository;
import app.sevacenter.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The filter's own tenant check, with RLS out of the picture. End to end, RLS also hides a
 * foreign user from the re-read, so removing the check went unnoticed (mutation-tested). Here
 * the repository returns the user anyway, as it would if RLS were ever misconfigured.
 */
class StaffSessionFilterTest {

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StaffSessionFilter filter = new StaffSessionFilter(users);

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSessionIsRejectedOnAnotherTenantsHostEvenIfTheUserIsVisible() throws Exception {
        AppUser user = activeUser(TENANT_A);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        authenticate(user);
        TenantContext.set(TENANT_B);

        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).as("request must not reach the controller").isNull();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void theSameSessionPassesOnItsOwnTenantsHost() throws Exception {
        AppUser user = activeUser(TENANT_A);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        authenticate(user);
        TenantContext.set(TENANT_A);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static AppUser activeUser(long tenantId) {
        AppUser user = new AppUser(tenantId, "staff@x.example", "{bcrypt}x", "Staff", Role.TRUST_ADMIN);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    private static void authenticate(AppUser user) {
        StaffUser principal = new StaffUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
