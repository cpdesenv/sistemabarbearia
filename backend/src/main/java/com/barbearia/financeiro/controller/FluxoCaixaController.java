package com.barbearia.financeiro.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.dto.FluxoCaixaDto;
import com.barbearia.financeiro.service.FluxoCaixaService;

@RestController
@RequestMapping("/api/financeiro/fluxo-caixa")
@RequiredArgsConstructor
@Tag(name = "Fluxo de caixa")
public class FluxoCaixaController {

    private final FluxoCaixaService fluxoCaixaService;

    @GetMapping
    public FluxoCaixaDto obter() {
        return fluxoCaixaService.calcular();
    }
}
