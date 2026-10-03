package app.sevacenter.tenant;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import app.sevacenter.web.RequestSanityFilter;

/**
 * Wires up multi-tenancy:
 * <ul>
 *   <li>Every application {@link DataSource} is wrapped in a {@link TenantPinningDataSource}, so
 *       each connection checkout pins the RLS tenant from {@link TenantContext}.</li>
 *   <li>The {@link TenantResolutionFilter} runs ahead of the Spring Security chain
 *       (which registers at order -100), so the tenant is known before any connection is used.</li>
 * </ul>
 */
@Configuration
public class TenancyConfig {

    /** Every application DataSource pins the RLS tenant on checkout. */
    @Bean
    static BeanPostProcessor tenantPinningDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource ds && !(bean instanceof TenantPinningDataSource)
                        ? new TenantPinningDataSource(ds) : bean;
            }
        };
    }

    /** Runs first: malformed parameters never reach tenant resolution, security or controllers. */
    @Bean
    FilterRegistrationBean<RequestSanityFilter> requestSanityFilter() {
        FilterRegistrationBean<RequestSanityFilter> reg = new FilterRegistrationBean<>(new RequestSanityFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter(
            TenantRepository tenants,
            @Value("${sevacenter.tenant.allow-header-override:false}") boolean allowHeaderOverride,
            @Value("${sevacenter.tenant.base-domains:sevacenter.app,mandircenter.app}") List<String> baseDomains) {
        FilterRegistrationBean<TenantResolutionFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TenantResolutionFilter(tenants, allowHeaderOverride, baseDomains));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);   // after the sanity filter, before Spring Security (-100)
        return reg;
    }
}
