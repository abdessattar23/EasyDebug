package com.orange.igr.easydebug.starter.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TracingApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@TestPropertySource(properties = {
        "easydebug.url=",
        "management.tracing.sampling.probability=1.0",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class TracingWithoutStarterTest {

    @LocalServerPort
    private int port;

    @Test
    void injectedBuilderPropagatesAndStaticBuilderDoesNot() {
        ResponseEntity<String> boot = TraceRelay.relay(port, "boot");
        ResponseEntity<String> manual = TraceRelay.relay(port, "manual");
        assertThat(boot.getStatusCode().value()).isEqualTo(200);
        assertThat(manual.getStatusCode().value()).isEqualTo(200);
        assertThat(TraceRelay.downstream(boot.getBody())).contains(TraceRelay.TRACE);
        assertThat(TraceRelay.downstream(manual.getBody())).doesNotContain(TraceRelay.TRACE);
    }
}
