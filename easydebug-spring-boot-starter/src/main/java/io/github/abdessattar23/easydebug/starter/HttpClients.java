package io.github.abdessattar23.easydebug.starter;

import io.github.abdessattar23.easydebug.starter.http.BufferingCaptureInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.List;

/** Shared check so a builder customizer and a bean post-processor do not record the same call twice. */
final class HttpClients {

    private HttpClients() {
    }

    static boolean alreadyHooked(List<ClientHttpRequestInterceptor> interceptors, String hook) {
        for (ClientHttpRequestInterceptor interceptor : interceptors) {
            if (interceptor instanceof BufferingCaptureInterceptor capture && hook.equals(capture.hook())) {
                return true;
            }
        }
        return false;
    }
}
