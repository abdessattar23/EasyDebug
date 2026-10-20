package io.github.abdessattar23.easydebug.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class EventStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public int ingest(String body) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid JSON");
        }
        List<JsonNode> events = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(events::add);
        } else if (root.isObject() && root.has("events") && root.get("events").isArray()) {
            root.get("events").forEach(events::add);
        } else if (root.isObject()) {
            events.add(root);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expected an event or a list of events");
        }
        int stored = 0;
        for (JsonNode event : events) {
            if (event == null || !event.isObject()) {
                continue;
            }
            save((ObjectNode) event);
            stored++;
        }
        return stored;
    }

    public QueryResult query(String direction, String service, String host, String method, Integer status,
                             String kind, String text, Instant from, Instant to, int limit, int offset, boolean summary) {
        Where where = where(direction, service, host, method, status, kind, text, from, to);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM capture_event " + where.sql(), Long.class, where.args().toArray());
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        List<JsonNode> items = jdbc.query(
                "SELECT payload FROM capture_event " + where.sql() + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> read(rs, summary),
                args.toArray());
        return new QueryResult(items, total == null ? 0 : total, limit, offset);
    }

    public JsonNode find(String id) {
        List<JsonNode> rows = jdbc.query(
                "SELECT payload FROM capture_event WHERE id = ?",
                (rs, row) -> read(rs, false),
                id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JsonNode latestPolicy() {
        List<JsonNode> rows = jdbc.query(
                "SELECT payload FROM capture_event WHERE kind = 'capture.policy' ORDER BY occurred_at DESC, id DESC LIMIT 1",
                (rs, row) -> read(rs, false));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void save(ObjectNode event) {
        String id = text(event, "id");
        if (id == null) {
            id = UUID.randomUUID().toString();
            event.put("id", id);
        }
        if (text(event, "kind") == null) {
            event.put("kind", "unknown");
        }
        if (text(event, "host") == null) {
            String derived = hostOf(text(event, "url"));
            if (derived != null) {
                event.put("host", derived);
            }
        }
        Instant occurredAt = instant(event.get("occurredAt"));
        if (event.get("occurredAt") == null || event.get("occurredAt").isNull()) {
            event.put("occurredAt", occurredAt.toString());
        }
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event cannot be stored");
        }
        jdbc.update("""
                MERGE INTO capture_event
                    (id, kind, direction, service, hook, occurred_at, duration_ms, method, url, host, status, error, payload)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                text(event, "kind"),
                text(event, "direction"),
                text(event, "service"),
                text(event, "hook"),
                Timestamp.from(occurredAt),
                number(event, "durationMs"),
                text(event, "method"),
                text(event, "url"),
                text(event, "host"),
                intNumber(event, "status"),
                text(event, "error"),
                payload);
    }

    private JsonNode read(ResultSet rs, boolean summary) throws SQLException {
        try {
            JsonNode node = mapper.readTree(rs.getString("payload"));
            if (!summary || !node.isObject()) {
                return node;
            }
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            omitBody(copy, "request");
            omitBody(copy, "response");
            copy.put("bodyOmitted", true);
            return copy;
        } catch (Exception e) {
            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("error", "stored payload could not be parsed");
            fallback.put("payload", rs.getString("payload"));
            return fallback;
        }
    }

    private static void omitBody(ObjectNode event, String field) {
        JsonNode side = event.get(field);
        if (side instanceof ObjectNode object && object.has("body")) {
            object.remove("body");
            object.put("bodyOmitted", true);
        }
    }

    private Where where(String direction, String service, String host, String method, Integer status,
                        String kind, String text, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        equal(sql, args, "direction", direction);
        like(sql, args, "service", service);
        like(sql, args, "host", host);
        equal(sql, args, "method", method);
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        equal(sql, args, "kind", kind);
        if (text != null && !text.isBlank()) {
            sql.append(" AND LOWER(payload) LIKE ?");
            args.add("%" + text.toLowerCase() + "%");
        }
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at <= ?");
            args.add(Timestamp.from(to));
        }
        return new Where(sql.toString(), args);
    }

    private static void equal(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value.trim());
        }
    }

    private static void like(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND LOWER(").append(column).append(") LIKE ?");
            args.add("%" + value.trim().toLowerCase() + "%");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    private static Integer intNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank() || url.startsWith("/")) {
            return null;
        }
        String rest = url;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        String host = colon >= 0 ? authority.substring(0, colon) : authority;
        return host.isBlank() ? null : host;
    }

    private static Instant instant(JsonNode value) {
        if (value == null || value.isNull()) {
            return Instant.now();
        }
        try {
            if (value.isNumber()) {
                return Instant.ofEpochMilli(value.asLong());
            }
            return Instant.parse(value.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    public record QueryResult(List<JsonNode> items, long total, int limit, int offset) {
    }

    private record Where(String sql, List<Object> args) {
    }
}
