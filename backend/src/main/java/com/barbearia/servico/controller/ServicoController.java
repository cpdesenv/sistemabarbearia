package com.barbearia.servico.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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

import com.barbearia.servico.dto.AtualizarStatusServicoRequest;
import com.barbearia.servico.dto.SalvarServicoRequest;
import com.barbearia.servico.dto.ServicoDto;
import com.barbearia.servico.service.ServicoService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
@Tag(name = "Servicos")
public class ServicoController {

    private final ServicoService servicoService;

    @GetMapping
    public PagedModel<ServicoDto> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        Page<ServicoDto> pagina = servicoService.listar(nome, categoria, ativo, pageable);
        return new PagedModel<>(pagina);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ServicoDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(servicoService.obter(uuid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ServicoDto criar(@Valid @RequestBody SalvarServicoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return servicoService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ServicoDto> atualizar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarServicoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(servicoService.atualizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PatchMapping("/{uuid}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ServicoDto> atualizarStatus(@PathVariable UUID uuid,
            @Valid @RequestBody AtualizarStatusServicoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(servicoService.atualizarStatus(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }
}
