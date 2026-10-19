package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.AuthService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import com.skillmap.api.domain.model.IssuedSession;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.presentation.dto.AuthResponse;
import com.skillmap.api.presentation.dto.ChangePasswordRequest;
import com.skillmap.api.presentation.dto.DeleteAccountRequest;
import com.skillmap.api.presentation.dto.LoginRequest;
import com.skillmap.api.presentation.dto.RegisterRequest;
import com.skillmap.api.presentation.dto.UpdateGoalRequest;
import com.skillmap.api.presentation.dto.UpdateProfileRequest;
import com.skillmap.api.presentation.dto.UserResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import com.skillmap.api.presentation.filter.BearerToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuentas y sesiones. register y login son públicos; logout y me (GET, PATCH, DELETE y
 * me/password) exigen sesión (ver AuthenticationFilter). El token solo viaja en la respuesta
 * de register y login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        IssuedSession session = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return toResponse(authService.login(request.email(), request.password(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        // El filtro ya validó la sesión: aquí solo se cierra.
        BearerToken.extract(authorization).ifPresent(authService::logout);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        return toResponse(authService.getUser(userId));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                 @RequestBody UpdateProfileRequest request) {
        return toResponse(authService.updateProfile(userId, request.name(), request.headline()));
    }

    /** Objetivo profesional del usuario ({"goalId": n}). Su progreso por habilidad se conserva. */
    @PutMapping("/me/goal")
    public UserResponse updateGoal(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                   @RequestBody(required = false) UpdateGoalRequest request) {
        return toResponse(authService.updateGoal(userId, request == null ? null : request.goalId()));
    }

    /** La sesión de esta petición sigue abierta; las demás del usuario se cierran. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                               @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                               @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest httpRequest) {
        String token = BearerToken.extract(authorization).orElseThrow(AuthenticationRequiredException::new);
        authService.changePassword(userId, token, request.currentPassword(), request.newPassword(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                         @RequestBody DeleteAccountRequest request,
                                         HttpServletRequest httpRequest) {
        authService.deleteAccount(userId, request.password(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private static AuthResponse toResponse(IssuedSession session) {
        return new AuthResponse(session.token(), toResponse(session.user()));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getHeadline(), user.getGoalId());
    }
}
