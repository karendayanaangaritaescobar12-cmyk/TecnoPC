package com.tecnopc.admin;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Basic credentials are for native clients; reject browser initiated mutations. */
final class DesktopRequestFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/api/admin/") || path.startsWith("/api/vendedor/") ||
                 path.startsWith("/api/tecnico/"));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        boolean mutation = !method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (mutation && (request.getHeader("Origin") != null || fetchSite != null)) {
            response.sendError(403);
            return;
        }
        if (mutation) {
            try {
                String type = request.getContentType();
                if (type == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type))) {
                    response.sendError(415);
                    return;
                }
            } catch (IllegalArgumentException e) {
                response.sendError(415);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
