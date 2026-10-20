package io.github.abdessattar23.easydebug.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EventController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 1000;

    private final EventStore store;

    public EventController(EventStore store) {
        this.store = store;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody String body) {
        int stored = store.ingest(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("stored", stored));
    }

    @GetMapping("/events")
    public Map<String, Object> list(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "full") String view) {
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int start = Math.max(offset, 0);
        boolean summary = "summary".equalsIgnoreCase(view);
        EventStore.QueryResult result = store.query(direction, service, host, method, status, kind, q,
                from, to, size, start, summary);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.items());
        body.put("total", result.total());
        body.put("limit", result.limit());
        body.put("offset", result.offset());
        body.put("view", summary ? "summary" : "full");
        if (summary) {
            body.put("note", "Summary omits request.body and response.body. GET /api/events/{id} returns the stored event.");
        }
        return body;
    }

    @GetMapping("/events/{id}")
    public JsonNode one(@PathVariable String id) {
        JsonNode event = store.find(id);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found");
        }
        return event;
    }

    @GetMapping("/policy")
    public ResponseEntity<JsonNode> policy() {
        JsonNode policy = store.latestPolicy();
        if (policy == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(policy);
    }
}
