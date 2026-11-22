package com.taskflow.security;

import com.taskflow.config.RateLimitProperties;
import com.taskflow.dto.common.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limits for public authentication and registration endpoints.
 *
 * <p>The store is intentionally in-process: no new infrastructure dependency is
 * introduced. For a multi-instance production deployment this store must be
 * replaced with a shared implementation (e.g. Redis or a database table with an
 * atomic increment) and {@code clientIp} must be derived from a trusted proxy
 * chain. All other security controls remain unchanged.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private enum Category { LOGIN, REFRESH, ACTIVATION, REGISTRATION }

    private final RateLimitProperties properties;
    private final JsonMapper jsonMapper;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** Test/support hook to reset counters without restarting the application. */
    public void clear() {
        buckets.clear();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return category(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Category category = category(request);
        if (category == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();

        if (category == Category.LOGIN) {
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
            String account = extractEmail(cachedRequest);
            String accountKey = account == null ? null : "login:account:" + account.toLowerCase(Locale.ROOT);
            String ipKey = "login:ip:" + clientIp;
            if (!checkAndRecord(ipKey, properties.loginPerIpLimit())
                    || (accountKey != null && failureCount(accountKey) >= properties.loginFailuresPerAccountLimit())) {
                reject(request, response);
                return;
            }
            chain.doFilter(cachedRequest, response);
            if (accountKey != null) {
                if (response.getStatus() >= 400) {
                    recordUnchecked(accountKey);
                } else {
                    buckets.remove(accountKey);
                }
            }
            return;
        }

        String key = switch (category) {
            case REFRESH -> "refresh:ip:" + clientIp;
            case ACTIVATION -> "activation:ip:" + clientIp;
            case REGISTRATION -> "registration:ip:" + clientIp;
            default -> throw new IllegalStateException("Unhandled rate-limit category");
        };
        int limit = switch (category) {
            case REFRESH -> properties.refreshPerIpLimit();
            case ACTIVATION -> properties.activationPerIpLimit();
            case REGISTRATION -> properties.registrationPerIpLimit();
            default -> throw new IllegalStateException("Unhandled rate-limit category");
        };
        if (!checkAndRecord(key, limit)) {
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private Category category(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && "/api/v1/auth/login".equals(path)) return Category.LOGIN;
        if ("POST".equals(method) && "/api/v1/auth/refresh".equals(path)) return Category.REFRESH;
        if ("/api/v1/auth/activate".equals(path)
                || "/api/v1/auth/activate/validate".equals(path)) return Category.ACTIVATION;
        if ("POST".equals(method) && "/api/v1/setup/admin".equals(path)) return Category.REGISTRATION;
        return null;
    }

    private String extractEmail(HttpServletRequest request) {
        try {
            byte[] body = ((CachedBodyHttpServletRequest) request).body();
            if (body.length == 0) return null;
            var node = jsonMapper.readTree(new String(body, StandardCharsets.UTF_8));
            if (node == null || !node.isObject() || node.get("email") == null) return null;
            String email = node.get("email").asString();
            return email == null || email.isBlank() ? null : email.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean checkAndRecord(String key, int limit) {
        Deque<Long> queue = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            prune(queue);
            if (queue.size() >= limit) return false;
            queue.add(System.currentTimeMillis());
            return true;
        }
    }

    private void recordUnchecked(String key) {
        Deque<Long> queue = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            prune(queue);
            queue.add(System.currentTimeMillis());
        }
    }

    private int failureCount(String key) {
        Deque<Long> queue = buckets.get(key);
        if (queue == null) return 0;
        synchronized (queue) {
            prune(queue);
            return queue.size();
        }
    }

    private void prune(Deque<Long> queue) {
        long cutoff = System.currentTimeMillis() - properties.windowSeconds() * 1000L;
        while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
            queue.pollFirst();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (buckets.size() > 5000) {
            buckets.entrySet().removeIf(entry -> {
                Deque<Long> queue = entry.getValue();
                synchronized (queue) {
                    return queue.isEmpty() || queue.peekLast() < System.currentTimeMillis()
                            - properties.windowSeconds() * 1000L;
                }
            });
        }
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.windowSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiErrorResponse error = ApiErrorResponse.of(429, "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later.", request.getRequestURI());
        jsonMapper.writeValue(response.getOutputStream(), error);
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        private byte[] body() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream delegate = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return delegate.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { }
                @Override public int read() { return delegate.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
        }
    }
}
