package com.orange.igr.easydebug.starter;

import com.orange.igr.easydebug.CapturePolicy;
import com.orange.igr.easydebug.starter.feign.FeignCaptureClient;
import feign.Client;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnClass(Client.class)
public class EasyDebugFeignAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor easyDebugFeignPostProcessor(CapturePolicy policy, ExchangeAssembler assembler,
                                                                EasyDebugRecorder recorder) {
        return new FeignPostProcessor(policy, assembler, recorder);
    }

    @Bean
    public HookStatus easyDebugFeignHook() {
        return new HookStatus("feign");
    }

    private static final class FeignPostProcessor implements BeanPostProcessor, AopInfrastructureBean {
        private final CapturePolicy policy;
        private final ExchangeAssembler assembler;
        private final EasyDebugRecorder recorder;

        private FeignPostProcessor(CapturePolicy policy, ExchangeAssembler assembler, EasyDebugRecorder recorder) {
            this.policy = policy;
            this.assembler = assembler;
            this.recorder = recorder;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof FeignCaptureClient || !(bean instanceof Client client)) {
                return bean;
            }
            return new FeignCaptureClient(client, policy, assembler, recorder);
        }
    }
}
