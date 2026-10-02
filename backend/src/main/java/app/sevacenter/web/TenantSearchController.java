package app.sevacenter.web;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TenantSearchController {
    private final JdbcTemplate jdbc;
    public TenantSearchController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/tenants/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        // Bind the user input as a parameter: the driver sends it separately from the SQL text,
        // so it can never change the query's structure.
        return jdbc.queryForList(
                "SELECT id, slug, name FROM tenant WHERE name ILIKE ?", "%" + q + "%");
    }
}
