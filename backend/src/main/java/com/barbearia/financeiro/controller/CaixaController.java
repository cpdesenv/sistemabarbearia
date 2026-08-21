package com.barbearia.financeiro.controller;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.financeiro.dto.CaixaDoDiaDto;
import com.barbearia.financeiro.service.ComandaService;

@RestController
@RequestMapping("/api/caixa")
@RequiredArgsConstructor
@Tag(name = "Caixa")
public class CaixaController {

    private final ComandaService comandaService;

    @GetMapping
    public CaixaDoDiaDto caixaDoDia(@RequestParam(required = false) LocalDate data) {
        return comandaService.calcularCaixaDoDia(data);
    }
}
