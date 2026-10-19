# Configuration

Spring relaxed binding applies. `EASYDEBUG_URL` and `easydebug.url` are the same property. A blank list means empty. A missing list means the default shown below.

## Starter

| Property | Environment | Default | Meaning |
|---|---|---|---|
| `easydebug.url` | `EASYDEBUG_URL` | empty | Collector base URL, for example `http://127.0.0.1:8090`. Empty disables capture and logs that once. |
| `easydebug.service` | `EASYDEBUG_SERVICE` | `spring.application.name`, then `unknown` | Value stored in `service` on every event. |
| `easydebug.max-body-bytes` | `EASYDEBUG_MAX_BODY_BYTES` | `1048576` | Prefix kept in `body`. `size` and `sha256` still cover the full bytes. `0` stores no prefix and sets `truncated` when the body is non-empty. |
| `easydebug.queue-capacity` | `EASYDEBUG_QUEUE_CAPACITY` | `10000` | In-memory queue. Overflow becomes a `capture.gap` on the next successful send. Minimum 1. |
| `easydebug.timeout` | `EASYDEBUG_TIMEOUT` | `2s` | Connect and read timeout of the ingest POST. |
| `easydebug.spool-memory-bytes` | `EASYDEBUG_SPOOL_MEMORY_BYTES` | `8388608` | Body bytes kept in memory before the rest spills to a temp file. The hash still covers every byte. |
| `easydebug.exclude-paths` | `EASYDEBUG_EXCLUDE_PATHS` | empty | Comma-separated path prefixes. `/actuator` skips `/actuator` and `/actuator/health`, not `/actuator2`. |
| `easydebug.exclude-hosts` | `EASYDEBUG_EXCLUDE_HOSTS` | empty | Comma-separated host names, without the port. |
| `easydebug.redact-headers` | `EASYDEBUG_REDACT_HEADERS` | `Authorization,Cookie,Set-Cookie,Proxy-Authorization` | Header names whose values are stored as `[redacted]`. The header name stays. |
| `easydebug.redact-query` | `EASYDEBUG_REDACT_QUERY` | `sig,signature,X-Amz-Signature,X-Amz-Security-Token` | Query names whose values are stored as `[redacted]`. The name stays. |
| `easydebug.correlation-keys` | `EASYDEBUG_CORRELATION_KEYS` | empty | MDC keys or header names copied into the convenience `correlation` field, first match wins. The full MDC map is always stored. |

Set a redaction property to an empty string to store raw values:

```yaml
easydebug:
  url: http://127.0.0.1:8090
  redact-headers: ""
  redact-query: ""
```

The collector URL is always excluded, so the ingest POST is not recorded as an application call. That rule is part of the policy event (`builtInExclude`). It is the only exclusion that is not an environment variable.

## Server

| Property | Environment | Default |
|---|---|---|
| `server.port` | `EASYDEBUG_SERVER_PORT` | `8090` |
| `spring.datasource.url` | `EASYDEBUG_JDBC_URL` | `jdbc:h2:file:./data/easydebug;DB_CLOSE_ON_EXIT=FALSE` |
| `spring.datasource.username` | `EASYDEBUG_JDBC_USER` | `sa` |
| `spring.datasource.password` | `EASYDEBUG_JDBC_PASSWORD` | empty |

Point `EASYDEBUG_JDBC_URL` at another JDBC database if you do not want the H2 file. The schema in `schema.sql` is H2 dialect (`MERGE`, `CLOB`).

Tomcat's form post limit on the server is 64MB so a batch of capped bodies can land. Raise `server.tomcat.max-http-form-post-size` if you raise `easydebug.max-body-bytes` and send large batches.

## Query defaults

`GET /api/events` returns at most 50 rows unless `limit` is set. `limit` is capped at 1000. `offset` pages through `total`, which is the full match count. The viewer sends `offset` from Previous and Next, so a match past the first page is still on screen. `view=summary` drops `request.body` and `response.body` from that response only and sets `bodyOmitted: true`. The stored row is unchanged. `GET /api/events/{id}` returns it, and the viewer shows both bodies from that response plus the stored JSON.
