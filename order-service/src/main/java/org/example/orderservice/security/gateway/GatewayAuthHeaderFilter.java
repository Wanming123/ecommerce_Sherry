package org.example.orderservice.security.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Trusts the identity headers api-gateway's JwtAuthFilterFunction sets after it has already
 * validated the JWT - order-service no longer parses or verifies the token itself. Only safe
 * as long as order-service is not reachable except through the gateway.
 */
public class GatewayAuthHeaderFilter extends OncePerRequestFilter {
    public static final String USER_ID_HEADER = "X-Auth-User-Id";
    public static final String ROLES_HEADER = "X-Auth-Roles";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (StringUtils.hasText(userId)) {
            List<GrantedAuthority> authorities = parseRoles(request.getHeader(ROLES_HEADER));
            var auth = new UsernamePasswordAuthenticationToken(Long.valueOf(userId), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .filter(StringUtils::hasText)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
