package org.egov.user.security.oauth2.custom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter to check for session idle timeout on each request.
 * Validates that token has not exceeded idle timeout period.
 * If idle, removes token and returns 401 Unauthorized.
 */
@Component
public class IdleSessionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(IdleSessionFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired(required = false)
    private IdleSessionManager idleSessionManager;

    @Autowired(required = false)
    private TokenStore tokenStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Only process if idle session manager is available
            if (idleSessionManager != null) {
                String token = extractToken(request);
                
                if (token != null) {
                    // Check if token is idle
                    if (idleSessionManager.isTokenIdle(token)) {
                        logger.warn("Token idle timeout exceeded for request: {}", request.getRequestURI());
                        
                        // Remove the idle token from store
                        if (tokenStore != null) {
                            try {
                                tokenStore.removeAccessToken(tokenStore.readAccessToken(token));
                            } catch (Exception e) {
                                logger.debug("Error removing idle token", e);
                            }
                        }
                        
                        // Remove activity record
                        idleSessionManager.removeActivity(token);
                        
                        // Return 401 Unauthorized
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Session has expired due to inactivity\"}");
                        return;
                    }
                    
                    // Token is active, refresh the activity timestamp
                    idleSessionManager.refreshActivity(token);
                }
            }
        } catch (Exception e) {
            logger.error("Error in idle session filter", e);
            // Continue processing on error (fail open)
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        String requestToken = request.getParameter("access_token");
        if (StringUtils.hasText(requestToken)) {
            return requestToken;
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Skip filter for public endpoints (login, register, health checks, etc.)
        String path = request.getRequestURI();
        
        return path.contains("/oauth/token") ||
               path.contains("/user/citizen/_create") ||
               path.contains("/user/_login") ||
               path.contains("/actuator") ||
               path.contains("/health");
    }
}
