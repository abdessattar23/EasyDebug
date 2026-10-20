package io.github.abdessattar23.easydebug.starter;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class EasyDebugEnabledCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("easydebug.url");
        if (url == null || url.isBlank()) {
            return ConditionOutcome.noMatch("easydebug.url is blank");
        }
        return ConditionOutcome.match("easydebug.url is set");
    }
}
