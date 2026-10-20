# EasyDebug

EasyDebug records inbound and outbound calls for a Spring Boot app and shows every one of them. Add the starter, set the collector URL, and run the server. There is no per-endpoint call to make, no partner list, and no status that is ignored.

Try it on a new app, or on the demo module in this repository.

## Five minutes

From `easydebug/`:

```text
mvn -q -DskipTests package
java -jar easydebug-server/target/easydebug-server-1.0.1.jar
```

In another shell:

```text
set EASYDEBUG_URL=http://127.0.0.1:8090
java -jar easydebug-demo/target/easydebug-demo-1.0.1.jar
curl http://127.0.0.1:8080/demo/outbound
curl http://127.0.0.1:8080/demo/boom
```

Open `http://127.0.0.1:8090`. You should see:

- a `capture.policy` row listing the hooks that installed
- an `IN` for `GET /demo/outbound`
- an `OUT` from WebClient to `/demo/downstream`
- an `IN` for `GET /demo/downstream`
- an `IN` for `GET /demo/boom` with the exception

On PowerShell, set the variable with `$env:EASYDEBUG_URL = "http://127.0.0.1:8090"`.

## Add it to another app

```xml
<dependency>
    <groupId>io.github.abdessattar23</groupId>
    <artifactId>easydebug-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

```yaml
easydebug:
  url: http://127.0.0.1:8090
```

`EASYDEBUG_URL` is the same setting. An empty URL leaves capture off and logs that once. A set URL turns capture on. There is no second switch.

Two paths are captured:

- A client built from Spring Boot's `WebClient.Builder`, `RestClient.Builder`, or `RestTemplateBuilder`, including one that is never stored as a bean.
- A `WebClient`, `RestClient`, `RestTemplate`, or `feign.Client` bean, including `WebClient.create()`, `RestClient.create()`, and `new RestTemplate()` when that object is the bean.

```java
WebClient client = webClientBuilder.build();
RestClient rest = restClientBuilder.build();
RestTemplate template = restTemplateBuilder.build();
```

A client created inside a method and never registered as a bean is not captured, unless it came from one of those builders. A reactive application (`spring.main.web-application-type=reactive`) records inbound calls through a web filter. A servlet application records them through a servlet filter.

## What you are looking at

| Module | Role |
|---|---|
| `easydebug-core` | Event model, redaction, body hash, async sender. No Spring. |
| `easydebug-spring-boot-starter` | The only dependency an application adds. |
| `easydebug-server` | Ingest, H2 storage, query API, viewer. |
| `easydebug-demo` | One inbound route and one outbound WebClient call. |

## Read next

- [Architecture](docs/architecture.md)
- [Every setting](docs/configuration.md)
- [Event contract](docs/capture-contract.md)
- [What is hooked, and what is not](docs/coverage.md)
