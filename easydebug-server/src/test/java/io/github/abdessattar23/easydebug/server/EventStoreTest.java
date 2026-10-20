package io.github.abdessattar23.easydebug.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:easydebug;DB_CLOSE_DELAY=-1")
class EventStoreTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void storesEveryEventIncludingAttachmentCallsAndReturnsThemUnfiltered() throws Exception {
        String batch = """
                [
                  {
                    "id": "att-1",
                    "kind": "http",
                    "direction": "OUT",
                    "service": "worker",
                    "hook": "webclient",
                    "occurredAt": "2026-10-12T12:00:00Z",
                    "method": "POST",
                    "url": "https://owf.example/attachment",
                    "host": "owf.example",
                    "status": 201,
                    "request": {"filename": "annexe.xlsx", "size": 12, "sha256": "abc", "truncated": false, "body": "PK", "bodyEncoding": "text", "headers": {}, "contentType": "application/octet-stream"}
                  },
                  {
                    "id": "att-1",
                    "kind": "http",
                    "direction": "OUT",
                    "service": "worker",
                    "hook": "webclient",
                    "occurredAt": "2026-10-12T12:00:01Z",
                    "method": "POST",
                    "url": "https://owf.example/attachment",
                    "host": "owf.example",
                    "status": 201,
                    "request": {"filename": "annexe.xlsx", "size": 12, "body": "PK-once"}
                  }
                ]
                """;

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(2));

        String listed = mvc.perform(get("/api/events").param("host", "owf.example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).contains("annexe.xlsx");
        assertThat(listed).contains("PK-once");

        mvc.perform(get("/api/events/att-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.body").value("PK-once"));

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"capture.policy","service":"worker","occurredAt":"2026-10-12T12:00:02Z","attributes":{"hooksInstalled":["servlet-inbound"]}}
                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("capture.policy"))
                .andExpect(jsonPath("$.attributes.hooksInstalled[0]").value("servlet-inbound"));

        String summary = mvc.perform(get("/api/events").param("view", "summary").param("q", "annexe.xlsx"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(summary).contains("bodyOmitted");
        assertThat(summary).doesNotContain("PK-once");
    }

    @Test
    void keepsAnEventThatArrivesWithoutAnId() throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"http","direction":"IN","url":"https://example.test/hook","method":"GET","status":204}
                """))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/events").param("host", "example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("items");
        assertThat(items.get(0).path("id").asText()).isNotBlank();
        assertThat(items.get(0).path("url").asText()).contains("/hook");
    }
}
