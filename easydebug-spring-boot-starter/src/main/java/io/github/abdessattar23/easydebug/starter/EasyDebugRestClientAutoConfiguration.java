package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.starter.http.BufferingCaptureInterceptor;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnClass(RestClient.class)
public class EasyDebugRestClientAutoConfiguration {

    @Bean
    public RestClientCustomizer easyDebugRestClientCustomizer(CapturePolicy policy, ExchangeAssembler assembler,
                                                              EasyDebugRecorder recorder) {
        BufferingCaptureInterceptor interceptor = new BufferingCaptureInterceptor(
                policy, assembler, recorder, "restclient");
        return builder -> builder.requestInterceptors(interceptors -> {
            if (!HttpClients.alreadyHooked(interceptors, "restclient")) {
                interceptors.add(interceptor);
            }
        });
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor easyDebugRestClientPostProcessor(CapturePolicy policy, ExchangeAssembler assembler,
                                                                     EasyDebugRecorder recorder) {
        BufferingCaptureInterceptor interceptor = new BufferingCaptureInterceptor(
                policy, assembler, recorder, "restclient");
        return new RestClientPostProcessor(interceptor);
    }

    @Bean
    public HookStatus easyDebugRestClientHook() {
        return new HookStatus("restclient");
    }

    private static final class RestClientPostProcessor implements BeanPostProcessor, AopInfrastructureBean {
        private final BufferingCaptureInterceptor interceptor;

        private RestClientPostProcessor(BufferingCaptureInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof RestClient client)) {
                return bean;
            }
            boolean[] present = {false};
            RestClient.Builder builder = client.mutate();
            builder.requestInterceptors(interceptors -> present[0] = HttpClients.alreadyHooked(interceptors, "restclient"));
            if (present[0]) {
                return bean;
            }
            builder.requestInterceptor(interceptor);
            return builder.build();
        }
    }
}
