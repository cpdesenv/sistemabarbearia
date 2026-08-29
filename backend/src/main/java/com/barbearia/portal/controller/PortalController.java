package com.barbearia.portal.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.portal.dto.PortalAgendamentoConfirmadoDto;
import com.barbearia.portal.dto.PortalAgendamentoRequest;
import com.barbearia.portal.dto.PortalProfissionalDto;
import com.barbearia.portal.dto.PortalServicoDto;
import com.barbearia.portal.service.PortalService;

/**
 * Portal publico de autoagendamento (Fase 9) — rota registrada em
 * {@code ROTAS_PUBLICAS} (SecurityConfig), sem autenticacao. Toda regra de
 * negocio (disponibilidade, conflito, antecedencia) e' delegada ao
 * {@link PortalService}, que reaproveita os mesmos services do painel.
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "Portal publico")
public class PortalController {

    private final PortalService portalService;

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("ativo", portalService.ativo());
    }

    @GetMapping("/servicos")
    public List<PortalServicoDto> listarServicos() {
        return portalService.listarServicos();
    }

    @GetMapping("/profissionais")
    public List<PortalProfissionalDto> listarProfissionais(@RequestParam List<UUID> servicoUuids) {
        return portalService.listarProfissionais(servicoUuids);
    }

    @GetMapping("/disponibilidade")
    public List<SlotDisponivelDto> consultarDisponibilidade(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam List<UUID> servicoUuids,
            @RequestParam(required = false) UUID profissionalUuid) {
        return portalService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }

    @PostMapping("/agendamentos")
    public ResponseEntity<PortalAgendamentoConfirmadoDto> criarAgendamento(
            @Valid @RequestBody PortalAgendamentoRequest requisicao, HttpServletRequest httpRequest) {
        PortalAgendamentoConfirmadoDto agendamento = portalService.criarAgendamento(requisicao, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamento);
    }
}
