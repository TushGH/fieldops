package com.fieldops.identity.infrastructure;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

final class AuthenticationErrors {
    private AuthenticationErrors() { }

    static void unauthenticated(HttpServletResponse response) throws IOException {
        write(response, 401, "UNAUTHENTICATED", "Authentication is required or credentials are invalid.");
    }

    static void forbidden(HttpServletResponse response) throws IOException {
        write(response, 403, "REQUEST_REJECTED", "The request could not be verified.");
    }

    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        // Only fixed internal constants reach this method, never request values.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
