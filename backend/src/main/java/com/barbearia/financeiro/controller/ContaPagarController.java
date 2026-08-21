package com.barbearia.financeiro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.domain.StatusContaPagar;
import com.barbearia.financeiro.dto.CancelarContaRequest;
import com.barbearia.financeiro.dto.ContaPagarDto;
import com.barbearia.financeiro.dto.CriarContaPagarRequest;
import com.barbearia.financeiro.service.ContaPagarService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/contas-pagar")
@RequiredArgsConstructor
@Tag(name = "Contas a pagar")
public class ContaPagarController {

    private final ContaPagarService contaPagarService;

    @GetMapping
    public List<ContaPagarDto> listar(@RequestParam(required = false) StatusContaPagar status) {
        return contaPagarService.listar(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ContaPagarDto criar(@Valid @RequestBody CriarContaPagarRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return contaPagarService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PostMapping("/{uuid}/pagar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ContaPagarDto> marcarPaga(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(contaPagarService.marcarPaga(uuid, principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ContaPagarDto> cancelar(@PathVariable UUID uuid,
            @Valid @RequestBody CancelarContaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(contaPagarService.cancelar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }
}
