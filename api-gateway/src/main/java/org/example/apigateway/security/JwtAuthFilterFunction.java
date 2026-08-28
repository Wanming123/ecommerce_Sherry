package org.example.apigateway.security;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.Map;

/**
 * Gateway-level JWT check for routes that require authentication. On success, the verified
 * identity is forwarded downstream as X-Auth-User-Id / X-Auth-Roles so services no longer need
 * to parse or verify the token themselves - they trust these headers instead (see each
 * service's GatewayAuthHeaderFilter). This only holds as long as those services are not
 * reachable except through the gateway.
 */
@Component
public class JwtAuthFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {
    public static final String USER_ID_HEADER = "X-Auth-User-Id";
    public static final String ROLES_HEADER = "X-Auth-Roles";

    private final JwtUtils jwtUtils;

    public JwtAuthFilterFunction(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        String token = parseBearerToken(request);
        if (token == null || !jwtUtils.validateToken(token)) {
            return unauthorized();
        }

        Long userId = jwtUtils.getIdFromToken(token);
        List<String> roles = jwtUtils.getRolesFromToken(token);
        ServerRequest forwarded = ServerRequest.from(request)
                .header(USER_ID_HEADER, String.valueOf(userId))
                .header(ROLES_HEADER, roles == null ? "" : String.join(",", roles))
                .build();
        return next.handle(forwarded);
    }

    private String parseBearerToken(ServerRequest request) {
        String headerAuth = request.headers().firstHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private ServerResponse unauthorized() throws Exception {
        return ServerResponse.status(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "Unauthorized", "message", "You may login and try again!"));
    }
}
