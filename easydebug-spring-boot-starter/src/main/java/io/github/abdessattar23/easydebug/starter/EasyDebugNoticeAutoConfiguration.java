package io.github.abdessattar23.easydebug.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class EasyDebugNoticeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EasyDebugNoticeAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "easydebug", name = "url", havingValue = "", matchIfMissing = true)
    public Object easyDebugCaptureOffNotice(Environment environment) {
        String url = environment.getProperty("easydebug.url");
        if (url == null || url.isBlank()) {
            log.info("EasyDebug capture is off because easydebug.url is empty.");
        }
        return new Object();
    }
}
