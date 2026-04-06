package org.egov.user.security.oauth2.custom;

import org.egov.user.domain.model.SecureUser;
import org.egov.user.web.contract.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;

import static org.egov.user.config.UserServiceConstants.USER_CLIENT_ID;

/**
 * Custom Token Services that enforces:
 * 1. Single active session per user (only one login at a time)
 * 2. Session idle timeout (30 minutes of inactivity by default)
 * 
 * When a new access token is created, any existing tokens for the same user
 * (matching username, tenantId, and user type) are revoked.
 * Activity is recorded for idle timeout tracking.
 */
@Component
public class SingleSessionTokenServices extends DefaultTokenServices {

    private static final Logger logger = LoggerFactory.getLogger(SingleSessionTokenServices.class);

    @Autowired
    private TokenStore tokenStore;

    @Autowired(required = false)
    private IdleSessionManager idleSessionManager;

    @Value("${auth.singleSession.enabled:true}")
    private boolean singleSessionEnabled;

    @PostConstruct
    public void init() {
        // Set tokenStore after autowiring is complete
        super.setTokenStore(tokenStore);
    }

    @Override
    public OAuth2AccessToken createAccessToken(final OAuth2Authentication authentication) {
        // If single session enforcement is enabled, revoke existing tokens before creating new one
        if (singleSessionEnabled && tokenStore != null) {
            revokeExistingTokens(authentication);
        }

        // Create the new access token
        OAuth2AccessToken token = super.createAccessToken(authentication);

        // Record initial activity for idle timeout tracking
        if (idleSessionManager != null && token != null) {
            idleSessionManager.recordActivity(token.getValue());
            logger.debug("Recorded initial activity for new token");
        }

        return token;
    }

    /**
     * Revoke all existing access and refresh tokens for the authenticated user.
     * Tokens are matched by username, tenantId, and user type to ensure
     * tenant-specific session enforcement.
     *
     * @param authentication the current OAuth2 authentication
     */
    private void revokeExistingTokens(final OAuth2Authentication authentication) {
        try {
            // Extract user information from authentication
            SecureUser secureUser = (SecureUser) authentication.getUserAuthentication().getPrincipal();
            User userInfo = secureUser.getUser();
            String username = userInfo.getUserName();
            String tenantId = userInfo.getTenantId();
            String userType = userInfo.getType();

            logger.info("Single session enforcement: Checking for existing tokens for user: {} tenant: {}", 
                username, tenantId);

            // Find all existing tokens for this user
            Collection<OAuth2AccessToken> tokens = tokenStore.findTokensByClientIdAndUserName(
                    USER_CLIENT_ID, username);

            logger.info("Found {} existing tokens for user: {}", tokens.size(), username);

            // Revoke tokens that match the same tenant and user type
            for (OAuth2AccessToken token : tokens) {
                if (shouldRevokeToken(token, username, tenantId, userType)) {
                    logger.info("Revoking existing token for user: {} tenant: {} - Invalidating previous session", 
                        username, tenantId);
                    
                    // Remove activity record for idle timeout tracking
                    if (idleSessionManager != null) {
                        idleSessionManager.removeActivity(token.getValue());
                    }
                    
                    // Remove access token
                    tokenStore.removeAccessToken(token);

                    // Remove associated refresh token
                    OAuth2RefreshToken refreshToken = token.getRefreshToken();
                    if (refreshToken != null) {
                        tokenStore.removeRefreshToken(refreshToken);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error revoking existing tokens for user", e);
            // Don't fail the login if token revocation fails
        }
    }

    /**
     * Check if a token should be revoked based on user details.
     * A token should be revoked if it belongs to the same user (username),
     * tenant, and user type.
     *
     * @param token the token to check
     * @param username the current user's username
     * @param tenantId the current user's tenant ID
     * @param userType the current user's type
     * @return true if the token should be revoked, false otherwise
     */
    private boolean shouldRevokeToken(final OAuth2AccessToken token, final String username,
                                      final String tenantId, final String userType) {
        if (token.getAdditionalInformation() == null ||
            !token.getAdditionalInformation().containsKey("UserRequest")) {
            return false;
        }

        Object userRequestObj = token.getAdditionalInformation().get("UserRequest");
        if (!(userRequestObj instanceof User)) {
            return false;
        }

        User tokenUserInfo = (User) userRequestObj;

        // Match by username, tenantId, and userType
        return username.equalsIgnoreCase(tokenUserInfo.getUserName()) &&
               tenantId.equalsIgnoreCase(tokenUserInfo.getTenantId()) &&
               userType.equalsIgnoreCase(tokenUserInfo.getType());
    }
}
