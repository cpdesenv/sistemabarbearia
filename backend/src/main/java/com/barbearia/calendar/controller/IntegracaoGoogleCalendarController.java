package com.barbearia.calendar.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.calendar.config.CalendarProperties;
import com.barbearia.calendar.dto.AgendamentoForaDeSincroniaDto;
import com.barbearia.calendar.dto.AtualizarModoCalendarioRequest;
import com.barbearia.calendar.dto.DefinirCalendarioProfissionalRequest;
import com.barbearia.calendar.dto.StatusIntegracaoDto;
import com.barbearia.calendar.dto.UrlAutorizacaoDto;
import com.barbearia.calendar.service.IntegracaoGoogleCalendarService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/integracoes/google-calendar")
@RequiredArgsConstructor
@Tag(name = "Integracao Google Calendar")
public class IntegracaoGoogleCalendarController {

    private final IntegracaoGoogleCalendarService integracaoService;
    private final CalendarProperties propriedades;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<StatusIntegracaoDto> status() {
        return ResponseEntity.ok(integracaoService.status());
    }

    @GetMapping("/conectar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UrlAutorizacaoDto> conectar(@AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest httpRequest) {
        String url = integracaoService.iniciarConexao(principal.getUsuario().getId(), httpRequest);
        return ResponseEntity.ok(new UrlAutorizacaoDto(url));
    }

    /**
     * Rota publica (ver SecurityConfig) — chamada pelo navegador de volta do
     * Google, sem JWT. A autorizacao real e feita pelo `state` de uso unico
     * validado dentro do service.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        String destino;
        if (error != null) {
            destino = propriedades.getFrontendCallbackUri() + "?erro=" + error;
        } else {
            try {
                integracaoService.processarCallback(code, state);
                destino = propriedades.getFrontendCallbackUri() + "?conectado=true";
            } catch (RuntimeException e) {
                destino = propriedades.getFrontendCallbackUri() + "?erro=falha_ao_conectar";
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino).build();
    }

    @PostMapping("/desconectar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desconectar(@AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest httpRequest) {
        integracaoService.desconectar(principal.getUsuario().getId(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/modo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtualizarModoCalendarioRequest> atualizarModo(
            @Valid @RequestBody AtualizarModoCalendarioRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                integracaoService.atualizarModo(requisicao, principal.getUsuario().getId(), httpRequest));
    }

    @PutMapping("/profissionais/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> definirCalendarioProfissional(@PathVariable UUID uuid,
            @Valid @RequestBody DefinirCalendarioProfissionalRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        integracaoService.definirCalendarioProfissional(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fora-de-sincronia")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<AgendamentoForaDeSincroniaDto>> foraDeSincronia() {
        return ResponseEntity.ok(integracaoService.listarForaDeSincronia());
    }

    @PostMapping("/ressincronizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Void> ressincronizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest httpRequest) {
        integracaoService.ressincronizar(principal.getUsuario().getId(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
