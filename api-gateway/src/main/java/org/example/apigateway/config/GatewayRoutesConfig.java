package org.example.apigateway.config;

import org.example.apigateway.security.JwtAuthFilterFunction;
import org.example.apigateway.security.RateLimitFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.addResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Routes defined in Java (rather than YAML) so the order-service route can attach
 * JwtAuthFilterFunction - authentication now happens once here instead of in each service.
 * RateLimitFilterFunction is applied first on every route, ahead of auth, so it can reject
 * excess traffic before spending any work on JWT validation or load balancing.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> ecommRoutes(RateLimitFilterFunction rateLimitFilterFunction) {
        return route("ecomm")
                .route(path("/api/v1/carts/**", "/api/v1/cartItems/**", "/api/v1/products/**",
                        "/api/v1/categories/**", "/api/v1/images/**"), http())
                .filter(rateLimitFilterFunction)
                .filter(lb("ecomm"))
                .filter(addResponseHeader("X-Response-Source", "api-gateway"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes(RateLimitFilterFunction rateLimitFilterFunction) {
        return route("user-service")
                .route(path("/api/v1/users/**", "/api/v1/auth/**"), http())
                .filter(rateLimitFilterFunction)
                .filter(lb("user-service"))
                .filter(addResponseHeader("X-Response-Source", "api-gateway"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceRoutes(RateLimitFilterFunction rateLimitFilterFunction,
                                                               JwtAuthFilterFunction jwtAuthFilterFunction) {
        return route("order-service")
                .route(path("/api/v1/orders/**"), http())
                .filter(rateLimitFilterFunction)
                .filter(jwtAuthFilterFunction)
                .filter(lb("order-service"))
                .filter(addResponseHeader("X-Response-Source", "api-gateway"))
                .build();
    }
}
