package com.studencollabfin.server.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studencollabfin.server.service.UserService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@SuppressWarnings("null")
public class SecurityController {

    @Autowired
    private UserService userService;

    @GetMapping("/login/success")
    public void loginSuccess(HttpServletResponse response) throws IOException {
        // (Removed: SecurityController is obsolete after OAuth2 removal)
        response.sendRedirect("http://localhost:5173/login-failed");
    }
}
