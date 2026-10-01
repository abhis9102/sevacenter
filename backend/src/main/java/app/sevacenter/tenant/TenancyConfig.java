package app.sevacenter.tenant;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Wires up multi-tenancy:
 * <ul>
 *   <li>Transaction management at HIGHEST_PRECEDENCE, so the transaction opens before the
 *       {@link RlsTenantAspect} sets the tenant GUC on that transaction's connection.</li>
 *   <li>The {@link TenantResolutionFilter} ahead of the Spring Security chain
 *       (which registers at order -100).</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class TenancyConfig {

    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter(
            TenantRepository tenants,
            @org.springframework.beans.factory.annotation.Value("${sevacenter.tenant.allow-header-override:false}")
            boolean allowHeaderOverride) {
        FilterRegistrationBean<TenantResolutionFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TenantResolutionFilter(tenants, allowHeaderOverride));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);   // before Spring Security (-100)
        return reg;
    }
}
