package com.tecnopc.admin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, per-process limits. Uses the container-resolved address, never raw forwarding headers. */
final class PublicRequestFilter extends OncePerRequestFilter {
    private static final int MAX_BODY = 65536;
    private static final int MAX_WINDOWS = 8192;
    private static final long WINDOW_MILLIS = 60_000;
    private static final long CLEANUP_INTERVAL_MILLIS = 10_000;
    private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);
    private final LongSupplier elapsedMillis;
    private long nextCleanup;
    private record Window(long expires, int count) {}

    PublicRequestFilter() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    PublicRequestFilter(LongSupplier elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
        nextCleanup = elapsedMillis.getAsLong() + CLEANUP_INTERVAL_MILLIS;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith("/api/public/");
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = path(request);
        String route=switch(path){
            case "/api/public/v2/pagos" -> "/api/public/pagos";
            case "/api/public/productos","/api/public/seguimiento","/api/public/pagos/recibo",
                 "/api/public/pagos","/api/public/solicitudes","/api/public/pagos/webhook","/api/public/chatbot" -> path;
            default -> "other";
        };
        int max=switch(route){
            case "/api/public/productos" -> 300;
            case "/api/public/seguimiento","/api/public/pagos/recibo" -> 15;
            case "/api/public/pagos","/api/public/solicitudes" -> 10;
            case "/api/public/pagos/webhook" -> 600;
            default -> 60;
        };
        if (!allow(request.getRemoteAddr() + ":" + route, max)) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "Demasiadas solicitudes; intenta de nuevo en un minuto");
            return;
        }
        if (!request.getMethod().equals("POST")) { chain.doFilter(request, response); return; }
        // Webhook authentication uses signed query parameters, not its optional body.
        if (!path.endsWith("/webhook")) {
            try {
                if (request.getContentType() == null ||
                    !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) {
                    reject(response, 415, "Usa Content-Type application/json"); return;
                }
            } catch (IllegalArgumentException ex) {
                reject(response, 415, "Tipo de contenido invalido"); return;
            }
        }
        if (request.getContentLengthLong() > MAX_BODY) { reject(response, 413, "Solicitud demasiado grande"); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) { reject(response, 413, "Solicitud demasiado grande"); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
        }, response);
    }

    private synchronized boolean allow(String key, int max) {
        long now = elapsedMillis.getAsLong();
        // Clean at most every ten seconds while requests arrive; no background thread.
        if (now >= nextCleanup) {
            windows.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
            nextCleanup = now + CLEANUP_INTERVAL_MILLIS;
        }
        Window window = windows.get(key);
        if (window == null || window.expires() <= now) {
            if (window == null && windows.size() >= MAX_WINDOWS) {
                // Expired entries go first. If all are active, admit the new visitor
                // by forgetting the least recently used entry instead of blocking everyone new.
                windows.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
                if (windows.size() >= MAX_WINDOWS) {
                    var oldest = windows.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
            window = new Window(now + WINDOW_MILLIS, 0);
        }
        if (window.count() >= max) return false;
        windows.put(key, new Window(window.expires(), window.count() + 1));
        return true;
    }

    private void reject(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setHeader("Cache-Control","no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"mensaje\":\"" + message + "\"}");
    }
}
