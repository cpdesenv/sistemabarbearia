package com.barbearia.ia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.ia.dto.AtualizarConfiguracaoIaRequest;
import com.barbearia.ia.dto.ConfiguracaoIaDto;
import com.barbearia.ia.service.ConfiguracaoIaService;

@RestController
@RequestMapping("/api/configuracoes/ia")
@RequiredArgsConstructor
@Tag(name = "Configuracao do agente de IA")
public class ConfiguracaoIaController {

    private final ConfiguracaoIaService configuracaoIaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ConfiguracaoIaDto> obter() {
        return ResponseEntity.ok(configuracaoIaService.obter());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfiguracaoIaDto> atualizar(@Valid @RequestBody AtualizarConfiguracaoIaRequest requisicao) {
        return ResponseEntity.ok(configuracaoIaService.atualizar(requisicao));
    }
}
