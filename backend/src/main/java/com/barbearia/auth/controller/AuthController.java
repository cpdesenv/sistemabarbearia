package com.barbearia.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.auth.dto.LoginResponse;
import com.barbearia.auth.dto.LogoutRequest;
import com.barbearia.auth.dto.RefreshRequest;
import com.barbearia.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacao")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest requisicao,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(requisicao, httpRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest requisicao,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(requisicao.refreshToken(), httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest requisicao) {
        authService.logout(requisicao.refreshToken());
    }
}
