package com.orange.igr.easydebug.starter;

import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.starter.http.BufferingCaptureInterceptor;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnClass(RestTemplate.class)
public class EasyDebugRestTemplateAutoConfiguration {

    @Bean
    public RestTemplateCustomizer easyDebugRestTemplateCustomizer(CapturePolicy policy, ExchangeAssembler assembler,
                                                                  EasyDebugRecorder recorder) {
        BufferingCaptureInterceptor interceptor = new BufferingCaptureInterceptor(
                policy, assembler, recorder, "resttemplate");
        return restTemplate -> {
            if (!HttpClients.alreadyHooked(restTemplate.getInterceptors(), "resttemplate")) {
                restTemplate.getInterceptors().add(interceptor);
            }
        };
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor easyDebugRestTemplatePostProcessor(CapturePolicy policy, ExchangeAssembler assembler,
                                                                       EasyDebugRecorder recorder) {
        BufferingCaptureInterceptor interceptor = new BufferingCaptureInterceptor(
                policy, assembler, recorder, "resttemplate");
        return new RestTemplatePostProcessor(interceptor);
    }

    @Bean
    public HookStatus easyDebugRestTemplateHook() {
        return new HookStatus("resttemplate");
    }

    private static final class RestTemplatePostProcessor implements BeanPostProcessor, AopInfrastructureBean {
        private final BufferingCaptureInterceptor interceptor;

        private RestTemplatePostProcessor(BufferingCaptureInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RestTemplate template
                    && !HttpClients.alreadyHooked(template.getInterceptors(), "resttemplate")) {
                template.getInterceptors().add(interceptor);
            }
            return bean;
        }
    }
}
