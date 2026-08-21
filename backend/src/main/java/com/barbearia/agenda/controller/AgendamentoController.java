package com.barbearia.agenda.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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

import com.barbearia.agenda.domain.StatusAgendamento;
import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.CancelarAgendamentoRequest;
import com.barbearia.agenda.dto.SalvarAgendamentoRequest;
import com.barbearia.agenda.exception.ConflitoAgendamentoException;
import com.barbearia.agenda.service.AgendamentoService;
import com.barbearia.shared.exception.ErroResposta;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
@Tag(name = "Agendamentos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    @GetMapping
    public List<AgendamentoDto> listar(
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            @RequestParam(required = false) UUID profissionalUuid,
            @RequestParam(required = false) UUID clienteUuid,
            @RequestParam(required = false) StatusAgendamento status) {
        return agendamentoService.listar(de, ate, profissionalUuid, clienteUuid, status);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<AgendamentoDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(agendamentoService.obter(uuid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public AgendamentoDto criar(@Valid @RequestBody SalvarAgendamentoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return agendamentoService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public ResponseEntity<AgendamentoDto> alterar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarAgendamentoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.alterar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PostMapping("/{uuid}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<AgendamentoDto> confirmar(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.confirmar(uuid, principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/iniciar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<AgendamentoDto> iniciar(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.iniciarAtendimento(uuid, principal.getUsuario().getId(),
                httpRequest));
    }

    @PostMapping("/{uuid}/finalizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<AgendamentoDto> finalizar(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.finalizar(uuid, principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/nao-compareceu")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO', 'BARBEIRO')")
    public ResponseEntity<AgendamentoDto> marcarNaoComparecimento(@PathVariable UUID uuid,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.marcarNaoComparecimento(uuid, principal.getUsuario().getId(),
                httpRequest));
    }

    @PostMapping("/{uuid}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'RECEPCAO')")
    public ResponseEntity<AgendamentoDto> cancelar(@PathVariable UUID uuid,
            @Valid @RequestBody CancelarAgendamentoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(agendamentoService.cancelar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @ExceptionHandler(ConflitoAgendamentoException.class)
    public ResponseEntity<ErroResposta> tratarConflito(ConflitoAgendamentoException ex, HttpServletRequest request) {
        ErroResposta corpo = ErroResposta.de(HttpStatus.CONFLICT.value(), "CONFLITO_AGENDAMENTO", ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
    }
}
