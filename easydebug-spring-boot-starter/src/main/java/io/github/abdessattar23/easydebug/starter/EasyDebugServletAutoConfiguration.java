package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.starter.http.InboundCaptureFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;

@AutoConfiguration
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class EasyDebugServletAutoConfiguration {

    @Bean
    public FilterRegistrationBean<InboundCaptureFilter> easyDebugInboundFilterRegistration(
            CapturePolicy policy, ExchangeAssembler assembler, EasyDebugRecorder recorder) {
        FilterRegistrationBean<InboundCaptureFilter> registration = new FilterRegistrationBean<>(
                new InboundCaptureFilter(policy, assembler, recorder));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("easyDebugInbound");
        return registration;
    }

    @Bean
    public HookStatus easyDebugServletHook() {
        return new HookStatus("servlet-inbound");
    }
}
