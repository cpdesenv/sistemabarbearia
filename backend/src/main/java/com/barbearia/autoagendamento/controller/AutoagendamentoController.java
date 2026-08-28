package com.barbearia.autoagendamento.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

import com.barbearia.agenda.dto.AgendamentoDto;
import com.barbearia.agenda.dto.SlotDisponivelDto;
import com.barbearia.autoagendamento.dto.ConfiguracaoAutoagendamentoDto;
import com.barbearia.autoagendamento.dto.CriarAutoagendamentoRequest;
import com.barbearia.autoagendamento.dto.ProfissionalPublicoDto;
import com.barbearia.autoagendamento.dto.ServicoPublicoDto;
import com.barbearia.autoagendamento.service.AutoagendamentoService;

/**
 * Rota publica (sem autenticacao — ver {@code SecurityConfig.ROTAS_PUBLICAS}
 * e {@code AutoagendamentoRateLimitingFilter}), Fase 12 do PRD.
 */
@RestController
@RequestMapping("/api/autoagendamento")
@RequiredArgsConstructor
@Tag(name = "Autoagendamento publico")
public class AutoagendamentoController {

    private final AutoagendamentoService autoagendamentoService;

    @GetMapping("/configuracao")
    public ConfiguracaoAutoagendamentoDto obterConfiguracao() {
        return autoagendamentoService.obterConfiguracao();
    }

    @GetMapping("/servicos")
    public List<ServicoPublicoDto> consultarServicos() {
        return autoagendamentoService.consultarServicos();
    }

    @GetMapping("/profissionais")
    public List<ProfissionalPublicoDto> consultarProfissionais() {
        return autoagendamentoService.consultarProfissionais();
    }

    @GetMapping("/disponibilidade")
    public List<SlotDisponivelDto> consultarDisponibilidade(
            @RequestParam LocalDate data,
            @RequestParam List<UUID> servicoUuids,
            @RequestParam(required = false) UUID profissionalUuid) {
        return autoagendamentoService.consultarDisponibilidade(data, servicoUuids, profissionalUuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoDto agendar(@Valid @RequestBody CriarAutoagendamentoRequest requisicao,
            HttpServletRequest httpRequest) {
        return autoagendamentoService.agendar(requisicao, httpRequest);
    }
}
