package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CaptureEvent;
import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.EventPoster;
import io.github.abdessattar23.easydebug.EventSender;
import io.github.abdessattar23.easydebug.JdkEventPoster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Conditional(EasyDebugEnabledCondition.class)
@EnableConfigurationProperties(EasyDebugProperties.class)
public class EasyDebugAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EasyDebugAutoConfiguration.class);

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public CapturePolicy easyDebugPolicy(EasyDebugProperties properties, Environment environment) {
        String service = properties.getService();
        if (service == null || service.isBlank()) {
            service = environment.getProperty("spring.application.name", "unknown");
        }
        return new CapturePolicy(
                service,
                properties.getUrl(),
                properties.getMaxBodyBytes(),
                properties.getQueueCapacity(),
                properties.getTimeout(),
                properties.getSpoolMemoryBytes(),
                properties.getExcludePaths(),
                properties.getExcludeHosts(),
                properties.getRedactHeaders(),
                properties.getRedactQuery(),
                properties.getCorrelationKeys());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public EventPoster easyDebugPoster(CapturePolicy policy) {
        return new JdkEventPoster(policy.ingestUrl(), policy.timeout());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public EventSender easyDebugSender(CapturePolicy policy, EventPoster poster) {
        return new EventSender(policy, poster);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public ExchangeAssembler easyDebugAssembler(CapturePolicy policy) {
        return new ExchangeAssembler(policy);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public EasyDebugRecorder easyDebugRecorder(EventSender sender, CapturePolicy policy) {
        return new EasyDebugRecorder(sender, policy);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public ApplicationListener<ApplicationReadyEvent> easyDebugPolicyEmitter(
            CapturePolicy policy, EasyDebugRecorder recorder, Environment environment) {
        return event -> {
            List<String> installed = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            event.getApplicationContext().getBeansOfType(HookStatus.class).values()
                    .forEach(hook -> installed.add(hook.id()));
            consider(skipped, installed, "servlet-inbound", "jakarta.servlet.Filter",
                    "jakarta.servlet not on the classpath");
            consider(skipped, installed, "webclient", "org.springframework.web.reactive.function.client.WebClient",
                    "spring-webflux not on the classpath");
            consider(skipped, installed, "restclient", "org.springframework.web.client.RestClient",
                    "RestClient not on the classpath");
            consider(skipped, installed, "resttemplate", "org.springframework.web.client.RestTemplate",
                    "RestTemplate not on the classpath");
            consider(skipped, installed, "rabbit-template", "org.springframework.amqp.rabbit.core.RabbitTemplate",
                    "spring-amqp not on the classpath");
            consider(skipped, installed, "rabbit-listener", "org.springframework.amqp.rabbit.core.RabbitTemplate",
                    "spring-amqp not on the classpath");
            consider(skipped, installed, "feign", "feign.Client", "feign not on the classpath");
            if ("reactive".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type"))) {
                consider(skipped, installed, "webflux-inbound", "org.springframework.web.server.WebFilter",
                        "spring-webflux not on the classpath");
            }
            CaptureEvent report = CaptureEvent.policy(policy.service(), policy.describe(installed, skipped));
            recorder.record(report);
            log.info("EasyDebug capture is on for service [{}] -> {}. Hooks installed: {}. Skipped: {}.",
                    policy.service(), policy.ingestUrl(), installed, skipped);
        };
    }

    private static void consider(List<String> skipped, List<String> installed, String id, String className, String absent) {
        if (installed.contains(id)) {
            return;
        }
        ClassLoader loader = EasyDebugAutoConfiguration.class.getClassLoader();
        if (!org.springframework.util.ClassUtils.isPresent(className, loader)) {
            skipped.add(id + ": " + absent);
        } else {
            skipped.add(id + ": library present but hook not registered");
        }
    }
}
