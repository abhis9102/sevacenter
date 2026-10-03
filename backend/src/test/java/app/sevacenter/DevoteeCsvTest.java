package app.sevacenter;

import static app.sevacenter.TestStaff.on;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import app.sevacenter.auth.RegistrationService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Devotee CSV export/import (M2 slice 2, ADR 0010): admin-only bulk PII, formula injection,
 * all-or-nothing imports, no reflected cell content, header-based mass assignment, limits.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DevoteeCsvTest {

    private static final String HEADER = "fullName,phone,email,addressLine,city,state,pincode,dateOfBirth,consentSource\n";
    private static final String ROW = "Lakshmi Iyer,98765 43210,lakshmi@example.org,12 Temple Street,Pune,Maharashtra,411001,1980-05-14,IN_PERSON\n";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private RegistrationService registration;

    private TestStaff staff;
    private String a;
    private MockHttpSession admin;
    private MockHttpSession leader;

    @BeforeEach
    void setUp() throws Exception {
        staff = new TestStaff(mvc, json, registration);
        a = staff.tenant("csv-a");
        admin = staff.loginAdmin(a);
        leader = staff.staff(a, admin, "LEADER");
    }

    // --- export ------------------------------------------------------------------------------

    @Test
    void onlyAdminsCanExportAndTheFileIsNeverCached() throws Exception {
        MockHttpSession member = staff.staff(a, admin, "MEMBER");
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(member)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(leader)).andExpect(status().isForbidden());
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                // User content inside: it must download, never render (DAST "persistent XSS", M2).
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void exportMatchesEvenWhenAcceptHeaderIsApplicationJson() throws Exception {
        mvc.perform(on(a, get("/api/v1/devotees/export")).session(admin).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
    }

    @Test
    void exportedCellsCannotRunAsSpreadsheetFormulas() throws Exception {
        createJson("{\"fullName\":\"=HYPERLINK(\\\"https://evil.example\\\",\\\"click\\\")\",\"phone\":\"9876543210\","
                + "\"city\":\"@SUM(1+1)\",\"state\":\"-2+3\",\"consentSource\":\"IN_PERSON\"}");
        List<CSVRecord> rows = export(admin);
        assertThat(rows).hasSize(1);
        for (String cell : rows.get(0).values()) {
            assertThat(cell).as(cell).doesNotStartWith("=").doesNotStartWith("+").doesNotStartWith("-").doesNotStartWith("@");
        }
        assertThat(rows.get(0).get("fullName")).isEqualTo("'=HYPERLINK(\"https://evil.example\",\"click\")");
        assertThat(rows.get(0).get("phone")).isEqualTo("'+919876543210");
    }

    @Test
    void exportContainsOnlyTheCallersTenant() throws Exception {
        String b = staff.tenant("csv-b");
        MockHttpSession adminB = staff.loginAdmin(b);
        mvc.perform(on(b, post("/api/v1/devotees")).session(adminB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Tenant B Devotee\",\"consentSource\":\"IN_PERSON\"}")).andExpect(status().isCreated());
        assertThat(export(admin)).isEmpty();
        assertThat(export(b, adminB)).extracting(r -> r.get("fullName")).containsExactly("Tenant B Devotee");
    }

    // --- import ------------------------------------------------------------------------------

    @Test
    void onlyAdminsCanImport() throws Exception {
        importCsv(leader, HEADER + ROW).andExpect(status().isForbidden());
        importCsv(admin, HEADER + ROW).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
    }

    @Test
    void anExportImportsBackUnchanged() throws Exception {
        createJson("{\"fullName\":\"=cmd\",\"phone\":\"+442079460958\",\"email\":\"a@b.example\",\"consentSource\":\"WRITTEN\"}");
        String exported = mvc.perform(on(a, get("/api/v1/devotees/export")).session(admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        importCsv(admin, exported).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        List<CSVRecord> rows = export(admin);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("fullName")).containsOnly("'=cmd");
        assertThat(rows).extracting(r -> r.get("phone")).containsOnly("'+442079460958");
        assertThat(rows).extracting(r -> r.get("consentSource")).containsOnly("WRITTEN");
    }

    @Test
    void oneBadRowRejectsTheWholeFileAndNamesTheLineWithoutEchoingIt() throws Exception {
        String bad = ROW.replace("lakshmi@example.org", "<script>alert(1)</script>");
        String body = importCsv(admin, HEADER + ROW + bad + ROW.replace("411001", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_rows"))
                .andExpect(jsonPath("$.rows[0].line").value(3))
                .andExpect(jsonPath("$.rows[0].field").value("email"))
                .andExpect(jsonPath("$.rows[1].line").value(4))
                .andExpect(jsonPath("$.rows[1].field").value("pincode"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("<script>", "alert");
        assertThat(export(admin)).isEmpty();
    }

    @Test
    void everyRowNeedsConsent() throws Exception {
        importCsv(admin, HEADER + ROW.replace("IN_PERSON", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.rows[0].field").value("consentSource"));
        importCsv(admin, HEADER + ROW.replace("IN_PERSON", "TELEPATHY"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.rows[0].field").value("consentSource"));
    }

    /** Extra columns could map to fields the API never accepts (tenant, audit): refuse the file. */
    @Test
    void unknownOrMissingColumnsRejectTheFile() throws Exception {
        importCsv(admin, HEADER.replace("\n", ",tenantId\n") + ROW.replace("\n", ",1\n"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_header"));
        importCsv(admin, HEADER.replace(",consentSource", "") + ROW.replace(",IN_PERSON", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_header"));
        importCsv(admin, HEADER.replace("\n", ",phone\n") + ROW.replace("\n", ",1\n"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_header"));
    }

    @Test
    void malformedEmptyAndOversizedFilesAreRejected() throws Exception {
        importCsv(admin, HEADER + "\"Lakshmi,98765 43210\n")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("malformed_csv"));
        importCsv(admin, HEADER).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("empty"));
        importCsv(admin, HEADER + ROW.repeat(5_001))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("too_many_rows"));
        assertThat(export(admin)).isEmpty();
    }

    @Test
    void excelsByteOrderMarkAndPhoneFormatsAreHandled() throws Exception {
        importCsv(admin, "﻿" + HEADER + ROW.replace("98765 43210", "'+91 98765-43210"))
                .andExpect(status().isOk());
        assertThat(export(admin)).extracting(r -> r.get("phone")).containsExactly("'+919876543210");
    }

    @Test
    void nulBytesInCellsAreARowErrorNotAServerError() throws Exception {
        importCsv(admin, HEADER + ROW.replace("Pune", "Pu\u0000ne"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.rows[0].field").value("city"));
    }

    @Test
    void trailingBlankRowsAreGracefullyIgnored() throws Exception {
        importCsv(admin, HEADER + ROW + ",,,,,,,,\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    // --- helpers -----------------------------------------------------------------------------

    private void createJson(String body) throws Exception {
        mvc.perform(on(a, post("/api/v1/devotees")).session(leader).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    private ResultActions importCsv(MockHttpSession session, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "devotees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/v1/devotees/import").file(file).session(session).with(csrf())
                .with(r -> { r.setServerName(a + ".sevacenter.app"); return r; }));
    }

    private List<CSVRecord> export(MockHttpSession session) throws Exception {
        return export(a, session);
    }

    private List<CSVRecord> export(String slug, MockHttpSession session) throws Exception {
        String body = mvc.perform(on(slug, get("/api/v1/devotees/export")).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new StringReader(body)).getRecords();
    }
}
