package app.sevacenter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the generated OpenAPI 3 document (served at /v3/api-docs,
 * browsable at /swagger-ui.html). springdoc discovers the endpoints; this just
 * sets the title/version/description shown to API consumers.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI sevaCenterOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("SevaCenter API")
                .version("v1")
                .description("Multi-tenant temple/trust management platform — devotees, "
                        + "donations (with 80G), and events. All endpoints are tenant-scoped "
                        + "and authorization is enforced server-side.")
                .contact(new Contact().name("SevaCenter"))
                .license(new License().name("Proprietary")));
    }
}
