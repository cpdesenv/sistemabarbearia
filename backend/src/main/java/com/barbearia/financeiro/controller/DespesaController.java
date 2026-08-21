package com.barbearia.financeiro.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.barbearia.financeiro.dto.CriarDespesaRequest;
import com.barbearia.financeiro.dto.DespesaDto;
import com.barbearia.financeiro.service.DespesaService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/despesas")
@RequiredArgsConstructor
@Tag(name = "Despesas")
public class DespesaController {

    private final DespesaService despesaService;

    @GetMapping
    public List<DespesaDto> listar(@RequestParam(required = false) LocalDate dataInicial,
            @RequestParam(required = false) LocalDate dataFinal) {
        return despesaService.listar(dataInicial, dataFinal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public DespesaDto criar(@Valid @RequestBody CriarDespesaRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return despesaService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }
}
