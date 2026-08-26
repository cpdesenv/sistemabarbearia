package com.barbearia.assinatura.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.assinatura.dto.AtualizarStatusPlanoAssinaturaRequest;
import com.barbearia.assinatura.dto.PlanoAssinaturaDto;
import com.barbearia.assinatura.dto.SalvarPlanoAssinaturaRequest;
import com.barbearia.assinatura.service.PlanoAssinaturaService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/planos-assinatura")
@RequiredArgsConstructor
@Tag(name = "Planos de assinatura")
public class PlanoAssinaturaController {

    private final PlanoAssinaturaService planoAssinaturaService;

    @GetMapping
    public List<PlanoAssinaturaDto> listar(@RequestParam(required = false) Boolean ativo) {
        return planoAssinaturaService.listar(ativo);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<PlanoAssinaturaDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(planoAssinaturaService.obter(uuid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public PlanoAssinaturaDto criar(@Valid @RequestBody SalvarPlanoAssinaturaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return planoAssinaturaService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<PlanoAssinaturaDto> atualizar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarPlanoAssinaturaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(planoAssinaturaService.atualizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PatchMapping("/{uuid}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<PlanoAssinaturaDto> atualizarStatus(@PathVariable UUID uuid,
            @Valid @RequestBody AtualizarStatusPlanoAssinaturaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(planoAssinaturaService.atualizarStatus(uuid, requisicao,
                principal.getUsuario().getId(), httpRequest));
    }
}
