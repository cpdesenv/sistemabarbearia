package com.barbearia.barbearia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.dto.AtualizarBarbeariaRequest;
import com.barbearia.barbearia.dto.BarbeariaDto;
import com.barbearia.barbearia.service.BarbeariaService;
import com.barbearia.shared.security.UsuarioAutenticado;

/**
 * Registro unico de configuracao da barbearia. Sem endpoint de criacao nem
 * listagem — a linha ja existe desde a migration V6 (ver docs/limitacoes.md).
 */
@RestController
@RequestMapping("/api/barbearia")
@RequiredArgsConstructor
@Tag(name = "Barbearia")
public class BarbeariaController {

    private final BarbeariaService barbeariaService;

    @GetMapping
    public ResponseEntity<BarbeariaDto> obter() {
        return ResponseEntity.ok(barbeariaService.obter());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BarbeariaDto> atualizar(@Valid @RequestBody AtualizarBarbeariaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        BarbeariaDto dto = barbeariaService.atualizar(requisicao, principal.getUsuario().getId(), httpRequest);
        return ResponseEntity.ok(dto);
    }
}
