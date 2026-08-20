package com.barbearia.horario.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.barbearia.horario.dto.BloqueioDto;
import com.barbearia.horario.dto.CriarBloqueioRequest;
import com.barbearia.horario.service.BloqueioService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/bloqueios")
@RequiredArgsConstructor
@Tag(name = "Bloqueios")
public class BloqueioController {

    private final BloqueioService bloqueioService;

    @GetMapping
    public PagedModel<BloqueioDto> listar(
            @RequestParam(required = false) UUID profissionalUuid,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            Pageable pageable) {
        Page<BloqueioDto> pagina = bloqueioService.listar(profissionalUuid, de, ate, pageable);
        return new PagedModel<>(pagina);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public BloqueioDto criar(@Valid @RequestBody CriarBloqueioRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return bloqueioService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void remover(@PathVariable UUID uuid, @AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest httpRequest) {
        bloqueioService.remover(uuid, principal.getUsuario().getId(), httpRequest);
    }
}
