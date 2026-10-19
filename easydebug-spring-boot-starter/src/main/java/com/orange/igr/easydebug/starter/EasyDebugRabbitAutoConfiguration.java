package com.orange.igr.easydebug.starter;

import com.orange.igr.easydebug.starter.rabbit.RabbitCapture;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;
import org.aopalliance.aop.Advice;

import java.lang.reflect.Method;
import java.util.Arrays;

@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@AutoConfigureBefore(RabbitAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnClass(RabbitTemplate.class)
public class EasyDebugRabbitAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanPostProcessor easyDebugRabbitPostProcessor(RabbitCapture capture) {
        return new RabbitPostProcessor(capture);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public RabbitCapture easyDebugRabbitCapture(ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        return new RabbitCapture(assembler, recorder);
    }

    @Bean
    public HookStatus easyDebugRabbitTemplateHook() {
        return new HookStatus("rabbit-template");
    }

    @Bean
    public HookStatus easyDebugRabbitListenerHook() {
        return new HookStatus("rabbit-listener");
    }

    private static final class RabbitPostProcessor implements BeanPostProcessor, AopInfrastructureBean {
        private final RabbitCapture capture;

        private RabbitPostProcessor(RabbitCapture capture) {
            this.capture = capture;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RabbitTemplate template) {
                template.addBeforePublishPostProcessors(capture.outbound());
            }
            if (bean instanceof AbstractMessageListenerContainer container) {
                Advice advice = capture.listenerAdvice();
                Advice[] current = currentAdvice(container);
                if (!Arrays.asList(current).contains(advice)) {
                    Advice[] next = Arrays.copyOf(current, current.length + 1);
                    next[next.length - 1] = advice;
                    container.setAdviceChain(next);
                }
            }
            return bean;
        }

        private static Advice[] currentAdvice(AbstractMessageListenerContainer container) {
            try {
                Method method = AbstractMessageListenerContainer.class.getDeclaredMethod("getAdviceChain");
                method.setAccessible(true);
                Object value = method.invoke(container);
                if (value instanceof Advice[] advice) {
                    return advice;
                }
            } catch (ReflectiveOperationException ignored) {
                // A container we cannot read still receives this advice alone.
            }
            return new Advice[0];
        }
    }
}
