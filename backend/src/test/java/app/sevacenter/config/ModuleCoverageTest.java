package app.sevacenter.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import app.sevacenter.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every API endpoint is either in a module (and so subject to per-module limits, ADR 0021) or on
 * the explicit list of non-module paths. A new staff endpoint added without a decision fails here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ModuleCoverageTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void everyApiPathIsAModuleOrDeliberatelyNotOne() {
        List<String> unmapped = mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(p -> p.startsWith("/api/v1/"))
                .filter(p -> ModuleAccessInterceptor.moduleOf(p).isEmpty())
                .filter(p -> ModuleAccessInterceptor.NOT_MODULES.stream().noneMatch(n -> p.equals(n) || p.startsWith(n)
                        || p.startsWith(n + "/")))
                .sorted().toList();
        assertThat(unmapped).as("API paths in no module and not listed as non-module").isEmpty();
    }

    @Test
    void prefixesMatchWholeSegmentsOnly() {
        assertThat(ModuleAccessInterceptor.moduleOf("/api/v1/devotees/12")).isPresent();
        assertThat(ModuleAccessInterceptor.moduleOf("/api/v1/devotees")).isPresent();
        assertThat(ModuleAccessInterceptor.moduleOf("/api/v1/devoteesx")).isEmpty();
        assertThat(ModuleAccessInterceptor.moduleOf("/api/v1/public/pujas")).isEmpty();
    }
}
