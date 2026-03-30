package com.chatbot.chatbot_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Cache<String, AtomicInteger> rateLimitCache;
    private final RateLimitConfig rateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String ip = resolveClientIp(request);
        AtomicInteger counter = rateLimitCache.get(ip, k -> new AtomicInteger(0));

        int current = counter.incrementAndGet();
        int limit = rateLimitConfig.getRefillPerMinute();

        if (current > limit) {
            log.warn("Rate limit exceeded for IP: {} ({}/{})", ip, current, limit);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Troppe richieste\",\"details\":\"Riprova tra un minuto\",\"status\":429}"
            );
            return false;
        }

        return true;
    }

    /**
     * Rispetta X-Forwarded-For se presente (reverse proxy / load balancer).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}