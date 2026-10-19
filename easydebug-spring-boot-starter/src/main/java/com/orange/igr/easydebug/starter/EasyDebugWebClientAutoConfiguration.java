package com.orange.igr.easydebug.starter;

import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.starter.http.WebClientCaptureFilter;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnClass(WebClient.class)
public class EasyDebugWebClientAutoConfiguration {

    @Bean
    public WebClientCustomizer easyDebugWebClientCustomizer(CapturePolicy policy, ExchangeAssembler assembler,
                                                            EasyDebugRecorder recorder) {
        WebClientCaptureFilter filter = new WebClientCaptureFilter(policy, assembler, recorder);
        return builder -> builder.filters(filters -> {
            if (!hasFilter(filters)) {
                filters.add(filter);
            }
        });
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor easyDebugWebClientPostProcessor(CapturePolicy policy, ExchangeAssembler assembler,
                                                                    EasyDebugRecorder recorder) {
        return new WebClientPostProcessor(new WebClientCaptureFilter(policy, assembler, recorder));
    }

    @Bean
    public HookStatus easyDebugWebClientHook() {
        return new HookStatus("webclient");
    }

    private static boolean hasFilter(Iterable<ExchangeFilterFunction> filters) {
        for (ExchangeFilterFunction filter : filters) {
            if (filter instanceof WebClientCaptureFilter) {
                return true;
            }
        }
        return false;
    }

    private static final class WebClientPostProcessor implements BeanPostProcessor, AopInfrastructureBean {
        private final WebClientCaptureFilter filter;

        private WebClientPostProcessor(WebClientCaptureFilter filter) {
            this.filter = filter;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof WebClient client)) {
                return bean;
            }
            boolean[] present = {false};
            WebClient.Builder builder = client.mutate();
            builder.filters(filters -> present[0] = hasFilter(filters));
            if (present[0]) {
                return bean;
            }
            builder.filter(filter);
            return builder.build();
        }
    }
}
