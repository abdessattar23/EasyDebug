package com.orange.igr.easydebug.starter.rabbit;

import com.orange.igr.easydebug.BodyCapture;
import com.orange.igr.easydebug.Defaults;
import com.orange.igr.easydebug.Payload;
import com.orange.igr.easydebug.starter.EasyDebugRecorder;
import com.orange.igr.easydebug.starter.ExchangeAssembler;
import com.orange.igr.easydebug.starter.MdcSnapshot;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records Rabbit publishes and listener deliveries. Expected waits are not filtered out. */
public final class RabbitCapture {

    private final ExchangeAssembler assembler;
    private final EasyDebugRecorder recorder;
    private final MethodInterceptor listenerAdvice = this::listen;

    public RabbitCapture(ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        this.assembler = assembler;
        this.recorder = recorder;
    }

    public MessagePostProcessor outbound() {
        return new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) {
                return capture(message, null, null);
            }

            @Override
            public Message postProcessMessage(Message message, Correlation correlation, String exchange, String routingKey) {
                return capture(message, exchange, routingKey);
            }
        };
    }

    public MethodInterceptor listenerAdvice() {
        return listenerAdvice;
    }

    private Message capture(Message message, String exchange, String routingKey) {
        try {
            MessageProperties properties = message.getMessageProperties();
            String usedExchange = exchange != null ? exchange : properties.getReceivedExchange();
            String usedKey = routingKey != null ? routingKey : properties.getReceivedRoutingKey();
            recorder.record(event(Defaults.DIRECTION_OUT, "rabbit-template", "PUBLISH",
                    usedExchange, usedKey, message, null, Instant.now(), System.nanoTime()));
        } catch (Exception ignored) {
            // keep the publish
        }
        return message;
    }

    private Object listen(MethodInvocation invocation) throws Throwable {
        Message message = find(invocation.getArguments());
        Instant started = Instant.now();
        long nanos = System.nanoTime();
        try {
            Object result = invocation.proceed();
            if (message != null) {
                recorder.record(event(Defaults.DIRECTION_IN, "rabbit-listener", "DELIVER",
                        message.getMessageProperties().getReceivedExchange(),
                        message.getMessageProperties().getReceivedRoutingKey(),
                        message, null, started, nanos));
            }
            return result;
        } catch (Throwable error) {
            if (message != null) {
                recorder.record(event(Defaults.DIRECTION_IN, "rabbit-listener", "DELIVER",
                        message.getMessageProperties().getReceivedExchange(),
                        message.getMessageProperties().getReceivedRoutingKey(),
                        message, error, started, nanos));
            }
            throw error;
        }
    }

    private com.orange.igr.easydebug.CaptureEvent event(String direction, String hook, String method,
                                                        String exchange, String routingKey, Message message,
                                                        Throwable error, Instant started, long nanos) {
        MessageProperties properties = message.getMessageProperties();
        byte[] body = message.getBody() == null ? new byte[0] : message.getBody();
        Payload payload = BodyCapture.capture(body, properties.getContentType(), null,
                recorder.policy().maxBodyBytes());
        return assembler.rabbit(direction, hook, method, exchange, routingKey, null, error,
                headers(properties), payload, MdcSnapshot.copy(), started, nanos);
    }

    private static Map<String, String> headers(MessageProperties properties) {
        Map<String, String> out = new LinkedHashMap<>();
        if (properties.getHeaders() != null) {
            properties.getHeaders().forEach((key, value) -> {
                if (key != null && value != null) {
                    out.put(key, String.valueOf(value));
                }
            });
        }
        if (properties.getMessageId() != null) {
            out.put("messageId", properties.getMessageId());
        }
        if (properties.getCorrelationId() != null) {
            out.put("correlationId", properties.getCorrelationId());
        }
        return out;
    }

    private static Message find(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                return message;
            }
        }
        return null;
    }
}
