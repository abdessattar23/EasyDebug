package io.github.abdessattar23.easydebug.starter.rabbit;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.EventSender;
import io.github.abdessattar23.easydebug.JsonMappers;
import io.github.abdessattar23.easydebug.starter.EasyDebugRecorder;
import io.github.abdessattar23.easydebug.starter.ExchangeAssembler;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitCaptureTest {

    @Test
    void recordsPublishAndDeliveryIncludingTheFailure() throws Throwable {
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        CapturePolicy policy = new CapturePolicy("demo", "http://127.0.0.1:9", 1000, 10,
                Duration.ofSeconds(1), null, null, null, "", "", "");
        EventSender sender = new EventSender(policy, json -> {
            bodies.add(json);
            return 202;
        });
        RabbitCapture capture = new RabbitCapture(new ExchangeAssembler(policy), new EasyDebugRecorder(sender, policy));
        try {
            Message message = MessageBuilder.withBody("{\"n\":1}".getBytes(StandardCharsets.UTF_8))
                    .andProperties(new MessageProperties())
                    .build();
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setReceivedExchange("commands");
            message.getMessageProperties().setReceivedRoutingKey("tickets");

            capture.outbound().postProcessMessage(message, null, "commands", "tickets");

            MethodInvocation invocation = mock(MethodInvocation.class);
            when(invocation.getArguments()).thenReturn(new Object[]{message});
            when(invocation.proceed()).thenThrow(new IllegalStateException("listener failed"));
            assertThatThrownBy(() -> capture.listenerAdvice().invoke(invocation))
                    .isInstanceOf(IllegalStateException.class);

            List<JsonNode> events = await(bodies, 2);
            assertThat(events).anyMatch(event ->
                    "rabbit".equals(event.path("kind").asText())
                            && "OUT".equals(event.path("direction").asText())
                            && "amqp://commands/tickets".equals(event.path("url").asText())
                            && event.path("request").path("body").asText().contains("\"n\":1"));
            assertThat(events).anyMatch(event ->
                    "IN".equals(event.path("direction").asText())
                            && event.path("error").asText().contains("listener failed"));
        } finally {
            sender.close();
        }
    }

    private static List<JsonNode> await(List<String> bodies, int count) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            List<JsonNode> events = events(bodies);
            if (events.size() >= count) {
                return events;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(events(bodies).toString());
    }

    private static List<JsonNode> events(List<String> bodies) throws Exception {
        List<String> snapshot;
        synchronized (bodies) {
            snapshot = List.copyOf(bodies);
        }
        List<JsonNode> events = new ArrayList<>();
        var mapper = JsonMappers.mapper();
        for (String body : snapshot) {
            for (JsonNode node : mapper.readTree(body)) {
                events.add(node);
            }
        }
        return events;
    }
}
