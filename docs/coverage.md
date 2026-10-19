# Coverage

EasyDebug records the calls that pass through the hooks below. A channel that is not hooked is named on the `capture.policy` event when its library is missing, or in this document when no library can see it. The product does not pretend a hand-built client was captured.

## Hooked

| Call | Requirement |
|---|---|
| Inbound HTTP | The app is a servlet application. The filter runs first and records the status, both bodies, the headers, and any exception that leaves the filter. |
| Inbound reactive HTTP | `spring.main.web-application-type=reactive`. A `WebFilter` records the status, both bodies, the headers, and any error that fails the chain. |
| Inbound multipart | The application parses the request with `getParts()` / `MultipartFile`. Part bytes are copied at that moment, including filename, size, and hash. |
| Outbound WebClient | The client is a `WebClient` bean, or it is built from Spring Boot's `WebClient.Builder` bean. |
| Outbound RestClient | The client is a `RestClient` bean, or it is built from Spring Boot's `RestClient.Builder` bean. |
| Outbound RestTemplate | The client is a `RestTemplate` bean, or it is built from `RestTemplateBuilder`. |
| Outbound Feign | A `feign.Client` bean. `@FeignClient` uses that bean. The wrapper records one row when several client beans delegate to each other with the same request. |
| Rabbit publish | A `RabbitTemplate` bean. The post-processor records the body, headers, exchange, and routing key. |
| Rabbit delivery | An `AbstractMessageListenerContainer` bean, which is how `@RabbitListener` is registered. Success and failure are both recorded. Expected waits are not skipped. |

Request and response bodies are spooled. Up to `easydebug.spool-memory-bytes` (8 MiB) stays in memory. The rest goes to a temp file that is deleted when the call finishes. The SHA-256 is of every byte. The stored `body` is only the first `easydebug.max-body-bytes`.

The business thread does not wait on the collector. `EventSender.submit` returns immediately. A full queue or a rejected POST shows up later as `capture.gap`.

## Trace context

EasyDebug does not create a trace and does not add a `traceparent` header. When the request already has a W3C `traceparent` header, or MDC already has `traceId`, that id is copied to `attributes.traceId`. A missing header stays missing.

`WebClient.builder()` is the static factory. Spring Boot's observation customizer is not applied to it, so a client built that way does not propagate the current trace. Registering that client as a bean lets EasyDebug record the call. The recording still does not add the header. Propagation stays with the application's client, which means the injected `WebClient.Builder` when Micrometer tracing is on the classpath.

## Not hooked

These do not appear as call events. Do not treat a missing row as proof the call did not happen.

| Client | Why |
|---|---|
| `WebClient.create()`, `WebClient.builder()`, `new RestTemplate()`, or `RestClient.create()` used in a method and not registered as a bean | The customizer runs on the Boot builder. The post-processor runs on beans. A local variable is neither. |
| `Feign.builder()` with its own `new Client.Default()`, when that client is not the Spring bean | The hook wraps `feign.Client` beans. |
| A hand-built Reactor Netty or JDK `HttpClient` | No Spring bean to customize. The ingest poster itself is a JDK client for this reason. |
| Non-blocking servlet reads (`ReadListener`) | The filter buffers the body and replays it. It does not implement non-blocking I/O. |
| A multipart request whose code never calls `getParts()` | The event is stored with `requestCaptureError`. The raw wire bytes are not reconstructed after the container has parsed them. |
| A process that is not this JVM | EasyDebug is a library, not a proxy. |

Async servlet dispatches (`DeferredResult`, `Callable`) keep the capturing response on the async context and record when the async listener completes. The filter does not run a second time on the async dispatch, so the row is not duplicated.

Rabbit advice is appended to the container's current chain. If that chain cannot be read, the container receives this advice on its own. That case is unlikely on Spring Boot's listener containers and would drop advice another component had already installed.

## Memory

Hashing a body means reading it. The spool keeps that off the heap past 8 MiB, but the application still receives the bytes it asked for. A `WebClient` response is replayed from the spool as the caller reads it, so the filter does not keep a second full copy in a single buffer. A `RestTemplate` or `RestClient` response is held in the spool until the caller closes the response.

Very large uploads increase disk use for the life of the request. They do not disappear from the log: you still get `size`, `sha256`, `filename`, and a truncated prefix.

## What "nothing dropped" means here

- No partner, attachment, or status is skipped in code.
- Redaction keeps the header or query name and replaces the value with `[redacted]`. Clearing the redaction lists stores the raw value.
- Path and host skips happen only when you set `easydebug.exclude-paths` or `easydebug.exclude-hosts`.
- The collector URL is excluded so ingest does not record itself. The policy event says so.
- A call that fails still produces an event with `error` and `attributes.stack`.
- A call that cannot be queued or is rejected produces a gap event, not a silent return.
