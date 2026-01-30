package com.studencollabfin.server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String contentType = httpRequest.getContentType();

        // Log upload requests
        if (uri.contains("/api/uploads") || uri.contains("pod-files")) {
            System.out.println("\n📍 REQUEST: " + method + " " + uri);
            System.out.println("   Content-Type: " + contentType);
            System.out.println("   Content-Length: " + httpRequest.getContentLength());
        }

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            System.err.println("❌ Filter Exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
