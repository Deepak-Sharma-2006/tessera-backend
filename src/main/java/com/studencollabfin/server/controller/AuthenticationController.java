package com.studencollabfin.server.controller;

import com.studencollabfin.server.config.JwtUtil;
import com.studencollabfin.server.dto.AuthenticationRequest;
import com.studencollabfin.server.dto.AuthenticationResponse;
import com.studencollabfin.server.dto.RegisterRequest;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthenticationController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthenticationController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@RequestBody AuthenticationRequest request, HttpServletResponse response) {
        try {
            User user = userService.authenticate(request.getEmail(), request.getPassword());
            final String jwt = jwtUtil.generateToken(user.getEmail());

            // Set token as httpOnly cookie for session persistence
            Cookie cookie = new Cookie("token", jwt);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
            // For local dev we keep secure=false; in production set secure=true
            cookie.setSecure(false);
            response.addCookie(cookie);

            return ResponseEntity.ok(new AuthenticationResponse(
                    jwt,
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.isProfileCompleted()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            boolean exists = userService.emailExists(email);

            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", exists);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User newUser = userService.register(request.getEmail(), request.getPassword());

            Map<String, Object> response = new HashMap<>();
            response.put("id", newUser.getId());
            response.put("email", newUser.getEmail());
            response.put("message", "User registered successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = "token", required = false) String tokenCookie) {
        try {
            String jwt = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            } else if (tokenCookie != null && !tokenCookie.isBlank()) {
                jwt = tokenCookie;
            }

            if (jwt == null) {
                // Return 200 with null body when not authenticated so frontend
                // can gracefully handle unauthenticated users without a 401 network error
                return ResponseEntity.ok().body(null);
            }

            String email = jwtUtil.getUsernameFromToken(jwt);
            User user = userService.findByEmail(email);

            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        try {
            Cookie cookie = new Cookie("token", "");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            response.addCookie(cookie);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Logout failed");
        }
    }
}
