package com.studencollabfin.server.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.io.IOException;

@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class SecurityController {

    @GetMapping("/login/success")
    public void loginSuccess(HttpServletResponse response) throws IOException {
        // (Removed: SecurityController is obsolete after OAuth2 removal)
        response.sendRedirect("http://localhost:5173/login-failed");
    }
}
