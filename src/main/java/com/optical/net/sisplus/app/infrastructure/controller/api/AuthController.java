package com.optical.net.sisplus.app.infrastructure.controller.api;

import com.optical.net.sisplus.app.infrastructure.config.RateLimitingConfig;
import com.optical.net.sisplus.app.infrastructure.entity.Admin;
import com.optical.net.sisplus.app.infrastructure.service.AuthService;
import com.optical.net.sisplus.app.infrastructure.web.AuthResponse;
import com.optical.net.sisplus.app.infrastructure.web.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * ============================================================
 *  AUTH CONTROLLER — Versión reforzada
 * ============================================================
 *
 * MEJORAS DE SEGURIDAD:
 *  1. Integra bloqueo progresivo: registra fallos en RateLimitingConfig
 *     y limpia el contador tras login exitoso
 *  2. Sanitiza username antes de autenticar (previene XSS/inyección)
 *  3. Mensajes de error genéricos — no revela si el usuario existe o no
 *  4. Log de auditoría en fallos y éxitos
 *  5. Protección contra username nulo/vacío
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final RateLimitingConfig rateLimitingConfig;

    public AuthController(AuthenticationManager authenticationManager,
                          AuthService authService,
                          RateLimitingConfig rateLimitingConfig) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.rateLimitingConfig = rateLimitingConfig;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip = getClientIP(httpRequest);

        // ----------------------------------------------------------
        // Validación básica de entrada
        // ----------------------------------------------------------
        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(400).body(AuthResponse.builder()
                    .success(false)
                    .message("Usuario y contraseña son requeridos")
                    .build());
        }

        // Sanitizar username (solo alfanumérico, punto, guión, guión bajo)
        String safeUsername = request.getUsername().trim()
                .replaceAll("[^a-zA-Z0-9._\\-]", "");

        // Validar longitud (previene payloads enormes en el campo)
        if (safeUsername.length() > 50 || request.getPassword().length() > 128) {
            return ResponseEntity.status(400).body(AuthResponse.builder()
                    .success(false)
                    .message("Credenciales inválidas")
                    .build());
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(safeUsername, request.getPassword())
            );

            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(authentication);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext);

            // Login exitoso: limpiar contador de fallos para esta IP
            rateLimitingConfig.clearLoginFailures(ip);

            authService.updateLastLogin(safeUsername);
            Admin admin = authService.findByUsername(safeUsername);

            String redirectUrl = (admin.getRoles() != null && admin.getRoles().contains("ADMIN"))
                    ? "/dashboard"
                    : "/cargues";

            log.info("[AUTH] Login exitoso — usuario: {}, IP: {}, roles: {}", safeUsername, ip, admin.getRoles());

            return ResponseEntity.ok(AuthResponse.builder()
                    .success(true)
                    .message("Login exitoso")
                    .username(admin.getUsername())
                    .redirectUrl(redirectUrl)
                    .roles(admin.getRoles())
                    .build());

        } catch (BadCredentialsException e) {
            // Registrar el fallo para bloqueo progresivo
            rateLimitingConfig.recordLoginFailure(ip);

            long remaining = rateLimitingConfig.getBlockedSecondsRemaining(ip);
            log.warn("[AUTH] Credenciales incorrectas — IP: {}", ip);

            // Mensaje genérico — NO revelar si el usuario existe o no
            String message = remaining > 0
                    ? String.format("Demasiados intentos. IP bloqueada por %d segundos.", remaining)
                    : "Credenciales incorrectas";

            return ResponseEntity.status(401).body(AuthResponse.builder()
                    .success(false)
                    .message(message)
                    .build());

        } catch (Exception e) {
            log.error("[AUTH] Error inesperado en login — IP: {}", ip, e);
            return ResponseEntity.status(500).body(AuthResponse.builder()
                    .success(false)
                    .message("Error interno. Intente nuevamente.")
                    .build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(AuthResponse.builder()
                .success(true)
                .message("Sesión cerrada exitosamente")
                .redirectUrl("/login")
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = auth.getName();
        Admin admin = authService.findByUsername(username);

        return ResponseEntity.ok(AuthResponse.builder()
                .success(true)
                .username(admin.getUsername())
                .roles(admin.getRoles())
                .build());
    }

    private String getClientIP(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}