package neo.sketchware.marketplace;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Shared conservative request pacing and Retry-After handling for Sketchlib catalog hosts. */
public final class SketchlibHttpClient {
    private static final long MIN_REQUEST_INTERVAL_MS = 1_100L;
    private static final long MAX_CLIENT_WAIT_MS = 5_000L;
    private static final ConcurrentHashMap<String, HostState> HOST_STATES = new ConcurrentHashMap<>();

    private SketchlibHttpClient() { }

    public static void beforeRequest(URL url) throws IOException {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null || url.getHost().isBlank()) {
            throw new IOException("Sketchlib requests require a valid HTTPS URL.");
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        HostState state = HOST_STATES.computeIfAbsent(host, ignored -> new HostState());
        synchronized (state) {
            while (true) {
                long now = System.currentTimeMillis();
                long waitUntil = Math.max(state.nextAllowedAt, state.retryAfterAt);
                long wait = Math.max(0L, waitUntil - now);
                if (wait > MAX_CLIENT_WAIT_MS) {
                    throw new IOException("The " + host + " API is rate-limited. Try again in about "
                            + Math.max(1L, (wait + 999L) / 1000L) + " seconds.");
                }
                if (wait > 0L) {
                    try {
                        state.wait(wait);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("The request was cancelled while waiting for API rate limits.", interrupted);
                    }
                    // Recalculate after waking: another queued request may have claimed the next slot.
                    continue;
                }
                state.nextAllowedAt = now + MIN_REQUEST_INTERVAL_MS;
                break;
            }
        }
    }

    public static void recordResponse(URL url, int status, String retryAfterHeader) {
        if (url == null || url.getHost() == null) return;
        if (status != 429 && !(status == 503 && retryAfterHeader != null && !retryAfterHeader.isBlank())) return;
        long retryAt = parseRetryAfter(retryAfterHeader);
        if (retryAt <= System.currentTimeMillis()) retryAt = System.currentTimeMillis() + 5_000L;
        HostState state = HOST_STATES.computeIfAbsent(url.getHost().toLowerCase(Locale.ROOT), ignored -> new HostState());
        synchronized (state) {
            state.retryAfterAt = Math.max(state.retryAfterAt, retryAt);
            state.notifyAll();
        }
    }

    public static IOException httpError(URL url, int status, String retryAfterHeader) {
        recordResponse(url, status, retryAfterHeader);
        if (status == 429 || status == 503 && retryAfterHeader != null) {
            long until = parseRetryAfter(retryAfterHeader);
            if (until <= System.currentTimeMillis()) until = System.currentTimeMillis() + 5_000L;
            return new IOException("API rate limit or temporary server limit reached (HTTP " + status + "). Try again in about "
                    + Math.max(1L, (until - System.currentTimeMillis() + 999L) / 1000L) + " seconds.");
        }
        return new IOException("Server returned HTTP " + status + (url == null ? "" : " for " + url.getHost()));
    }

    private static long parseRetryAfter(String header) {
        if (header == null || header.trim().isEmpty()) return 0L;
        String value = header.trim();
        try {
            long seconds = Long.parseLong(value);
            return System.currentTimeMillis() + Math.max(0L, Math.min(seconds, 86_400L)) * 1000L;
        } catch (NumberFormatException ignored) { }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            try { return Instant.parse(value).toEpochMilli(); } catch (Exception ignoredAgain) { return 0L; }
        }
    }

    private static final class HostState {
        long nextAllowedAt;
        long retryAfterAt;
    }
}
