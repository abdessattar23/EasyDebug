package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.starter.http.InboundWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@AutoConfigureAfter(EasyDebugAutoConfiguration.class)
@Conditional(EasyDebugEnabledCondition.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(WebFilter.class)
public class EasyDebugWebFluxAutoConfiguration {

    @Bean
    public WebFilter easyDebugInboundWebFilter(CapturePolicy policy, ExchangeAssembler assembler,
                                               EasyDebugRecorder recorder) {
        return new InboundWebFilter(policy, assembler, recorder);
    }

    @Bean
    public HookStatus easyDebugWebFluxHook() {
        return new HookStatus("webflux-inbound");
    }
}
