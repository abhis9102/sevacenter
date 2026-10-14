package app.sevacenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The API contract (ADR 0035): docs/api/openapi.json must match what the code serves. The staging
 * DAST scan is driven by this file (the spec isn't reachable on staging), and its git diff shows
 * exactly which endpoints a change touches. After an API change:
 * {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true}, then commit the file.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    static final Path CONTRACT = Path.of("..", "docs", "api", "openapi.json");

    @Autowired
    private MockMvc mvc;

    @Test
    void theCommittedSpecMatchesTheCode() throws Exception {
        String served = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String normalised = normalise(served);
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, normalised);
        }
        assertThat(Files.exists(CONTRACT))
                .as("docs/api/openapi.json is missing: run with -Dopenapi.update=true").isTrue();
        assertThat(Files.readString(CONTRACT))
                .as("the API changed but docs/api/openapi.json didn't: run with -Dopenapi.update=true and commit it")
                .isEqualTo(normalised);
    }

    /** Stable bytes: sorted keys, pretty-printed, no servers block (it depends on the host). */
    static String normalise(String spec) {
        JsonMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        JsonNode tree = mapper.readTree(spec);
        ((ObjectNode) tree).remove("servers");
        Object sorted = mapper.treeToValue(tree, java.util.Map.class);
        return mapper.writeValueAsString(sorted) + "\n";
    }
}
