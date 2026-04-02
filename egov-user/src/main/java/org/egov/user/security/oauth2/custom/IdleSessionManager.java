package org.egov.user.security.oauth2.custom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service to manage session idle timeout.
 * Tracks last activity time for each token and invalidates tokens after idle period.
 */
@Service
public class IdleSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(IdleSessionManager.class);
    private static final String TOKEN_ACTIVITY_PREFIX = "token:activity:";

    @Value("${auth.session.idle.timeout.minutes:30}")
    private long idleTimeoutMinutes;

    private RedisTemplate<String, Long> redisTemplate;

    public IdleSessionManager(RedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Record last activity time for a token.
     * Called when token is first created or on each API request.
     *
     * @param tokenValue the access token value
     */
    public void recordActivity(String tokenValue) {
        try {
            String key = TOKEN_ACTIVITY_PREFIX + tokenValue;
            long currentTime = System.currentTimeMillis();
            
            // Set activity timestamp with expiration matching token validity
            redisTemplate.opsForValue().set(key, currentTime, idleTimeoutMinutes, TimeUnit.MINUTES);
            
            logger.debug("Recorded activity for token: {}", tokenValue);
        } catch (Exception e) {
            logger.warn("Failed to record activity for token", e);
        }
    }

    /**
     * Check if token has exceeded idle timeout.
     *
     * @param tokenValue the access token value
     * @return true if token is idle (exceeded timeout), false if still active
     */
    public boolean isTokenIdle(String tokenValue) {
        try {
            String key = TOKEN_ACTIVITY_PREFIX + tokenValue;
            Long lastActivity = redisTemplate.opsForValue().get(key);
            
            if (lastActivity == null) {
                logger.debug("No activity record found for token: {}", tokenValue);
                return true;  // Token not found or expired
            }
            
            long currentTime = System.currentTimeMillis();
            long idleTimeMs = currentTime - lastActivity;
            long idleTimeoutMs = idleTimeoutMinutes * 60 * 1000;
            
            boolean isIdle = idleTimeMs > idleTimeoutMs;
            
            if (isIdle) {
                logger.debug("Token idle timeout exceeded. Idle time: {}ms, Timeout: {}ms", 
                    idleTimeMs, idleTimeoutMs);
            }
            
            return isIdle;
        } catch (Exception e) {
            logger.warn("Error checking token idle status", e);
            return false;  // On error, allow access (fail open)
        }
    }

    /**
     * Refresh the activity timestamp for a token (extend idle timeout).
     * Called on each successful API request to reset idle counter.
     *
     * @param tokenValue the access token value
     */
    public void refreshActivity(String tokenValue) {
        try {
            String key = TOKEN_ACTIVITY_PREFIX + tokenValue;
            long currentTime = System.currentTimeMillis();
            
            // Update activity timestamp
            redisTemplate.opsForValue().set(key, currentTime, idleTimeoutMinutes, TimeUnit.MINUTES);
            
            logger.debug("Refreshed activity for token: {}", tokenValue);
        } catch (Exception e) {
            logger.warn("Failed to refresh activity for token", e);
        }
    }

    /**
     * Remove activity record for a token (on logout).
     *
     * @param tokenValue the access token value
     */
    public void removeActivity(String tokenValue) {
        try {
            String key = TOKEN_ACTIVITY_PREFIX + tokenValue;
            redisTemplate.delete(key);
            logger.debug("Removed activity record for token: {}", tokenValue);
        } catch (Exception e) {
            logger.warn("Failed to remove activity record for token", e);
        }
    }
}
