package org.example.apigateway.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Map;

@Component
public class RateLimitFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {
    private static final String CONFIG_NAME = "perClient";

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitFilterFunction(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter(clientKey(request), CONFIG_NAME);
        if (!limiter.acquirePermission()) {
            return tooManyRequests();
        }
        return next.handle(request);
    }

    private String clientKey(ServerRequest request) {
        String forwardedFor = request.headers().firstHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("unknown");
    }

    private ServerResponse tooManyRequests() throws Exception {
        return ServerResponse.status(429)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "TooManyRequests", "message", "Rate limit exceeded, please slow down."));
    }
}
