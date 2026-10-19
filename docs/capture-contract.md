# Capture contract

Two promises:

1. A call that passed through an installed hook becomes one stored event.
2. A call that could not be delivered becomes part of a `capture.gap` event on the next successful send.

The viewer and `GET /api/events/{id}` show the stored JSON. Nothing is removed on read.

## Kinds

| `kind` | When |
|---|---|
| `http` | Servlet, WebClient, RestClient, or RestTemplate |
| `rabbit` | `RabbitTemplate` publish or `@RabbitListener` delivery |
| `capture.policy` | Once, when the application is ready |
| `capture.gap` | The previous batch could not be queued, or the collector rejected it with `4xx` |

`direction` is `IN` or `OUT` for calls. Policy and gap events leave it null.

## Call event

```json
{
  "id": "8f0c1c2e-1b2a-4f3a-9c1d-0a0b0c0d0e0f",
  "kind": "http",
  "direction": "OUT",
  "service": "easydebug-demo",
  "hook": "webclient",
  "occurredAt": "2026-10-12T16:00:00Z",
  "durationMs": 12,
  "method": "POST",
  "url": "https://files.example/upload?X-Amz-Signature=[redacted]&ok=1",
  "host": "files.example",
  "status": 201,
  "error": null,
  "correlation": null,
  "mdc": {},
  "request": {
    "headers": {"Authorization": "[redacted]", "Content-Type": "application/octet-stream"},
    "contentType": "application/octet-stream",
    "filename": "annexe.xlsx",
    "size": 4096,
    "sha256": "hex of the full bytes",
    "truncated": false,
    "body": "prefix or full text, or base64",
    "bodyEncoding": "text"
  },
  "response": { "headers": {}, "contentType": null, "filename": null, "size": 0, "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "truncated": false, "body": null, "bodyEncoding": null },
  "attributes": {}
}
```

`hook` is `servlet`, `webflux`, `webclient`, `restclient`, `resttemplate`, `feign`, `rabbit-template`, or `rabbit-listener`.

`mdc` is a snapshot of every MDC entry at the start of the call. `correlation` is set only when `easydebug.correlation-keys` names a key that is present in that map or in the request headers. It is a convenience copy. It is not required, and a missing value does not drop the event.

`error` is `exception class: message` when the call threw. `attributes.stack` holds the stack trace, truncated to `max-body-bytes` with the marker `... [truncated, N chars total]` and `attributes.stackTruncated: true` when that happens.

`attributes.traceId` is set when the request headers contain a W3C `traceparent` value, or when MDC already contains `traceId`. The field is absent when neither is present. EasyDebug does not invent the id and does not write it onto the outgoing call.

Servlet events put `remoteAddr` in `attributes`. Multipart events add `multipart: true`, `bodyIs: "parts"`, and `attributes.parts` (name, filename, content type, size, sha256, truncated). The request hash is the hash of the part bytes in order, not of the multipart framing. If the application never parses the multipart body, the event is still stored and `requestCaptureError` says `getParts()` was not called.

Rabbit events use `method` `PUBLISH` or `DELIVER` and `url` `amqp://{exchange}/{routingKey}`. `attributes.exchange` and `attributes.routingKey` repeat those values. Message headers are stringified onto the request headers. `correlationId` and `messageId` from the AMQP properties are included when present.

## Body fields

| Field | Meaning |
|---|---|
| `size` | Full byte length, including bytes that were not stored. |
| `sha256` | SHA-256 hex of the full bytes. |
| `truncated` | `true` when `size` is greater than the stored prefix. |
| `body` | The prefix. Null when the body is empty. |
| `bodyEncoding` | `text` or `base64`. Null when the body is empty. |
| `filename` | From `Content-Disposition`, or from the first `filename=` in a multipart body. |
| `contentType` | The header value, parameters included. |

Text types (`text/*`, JSON, XML, form-urlencoded, and a body with no content type that is valid UTF-8) are stored as text. Anything else is base64 of the prefix, so a binary annex is still visible and is not mistaken for text.

An empty body uses the SHA-256 of zero bytes: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

## Policy event

`attributes` contains `service`, `collectorUrl`, `maxBodyBytes`, `queueCapacity`, `timeout`, `spoolMemoryBytes`, `excludePaths`, `excludeHosts`, `builtInExclude`, `redactHeaders`, `redactQuery`, `correlationKeys`, `hooksInstalled`, `hooksSkipped`, and `coverage`.

`hooksInstalled` is the list of hooks whose auto-configuration loaded (`servlet-inbound`, `webflux-inbound`, `webclient`, `restclient`, `resttemplate`, `feign`, `rabbit-template`, `rabbit-listener`). `hooksSkipped` explains a hook whose library is absent, or a library that is present while the hook did not register. `webflux-inbound` is considered only when `spring.main.web-application-type` is `reactive`. On a servlet application it is absent from both lists.

## Gap event

```json
{
  "kind": "capture.gap",
  "hook": "sender",
  "error": "12 calls were not delivered",
  "attributes": {
    "missedCount": 12,
    "from": "2026-10-12T16:00:00Z",
    "until": "2026-10-12T16:00:02Z"
  }
}
```

`missedCount` is a count of calls, not a count of gap events. A rejected batch that already contained a gap adds that gap's `missedCount` back, so the number is not collapsed to 1.

## Ingest

`POST /api/events` with `Content-Type: application/json`.

- one object
- a JSON array of objects
- `{"events":[ ... ]}`

Response: `201` and `{"stored": n}`. The same `id` updates the existing row instead of inserting a second one. Invalid JSON is `400`. A stored event is not filtered by partner, hook, or status.

`GET /api/events` query parameters, all optional: `direction`, `service`, `host`, `method`, `status`, `kind`, `q`, `from`, `to`, `limit`, `offset`, `view`. `q` matches the stored JSON. `from` and `to` are ISO-8601 instants.
