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

import com.barbearia.financeiro.domain.StatusContaReceber;
import com.barbearia.financeiro.dto.CancelarContaRequest;
import com.barbearia.financeiro.dto.ContaReceberDto;
import com.barbearia.financeiro.dto.CriarContaReceberRequest;
import com.barbearia.financeiro.service.ContaReceberService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/contas-receber")
@RequiredArgsConstructor
@Tag(name = "Contas a receber")
public class ContaReceberController {

    private final ContaReceberService contaReceberService;

    @GetMapping
    public List<ContaReceberDto> listar(@RequestParam(required = false) StatusContaReceber status,
            @RequestParam(required = false) UUID clienteUuid) {
        return contaReceberService.listar(status, clienteUuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public ContaReceberDto criar(@Valid @RequestBody CriarContaReceberRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return contaReceberService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PostMapping("/{uuid}/receber")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ContaReceberDto> marcarRecebida(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(contaReceberService.marcarRecebida(uuid, principal.getUsuario().getId(),
                httpRequest));
    }

    @PostMapping("/{uuid}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ContaReceberDto> cancelar(@PathVariable UUID uuid,
            @Valid @RequestBody CancelarContaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(contaReceberService.cancelar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }
}
