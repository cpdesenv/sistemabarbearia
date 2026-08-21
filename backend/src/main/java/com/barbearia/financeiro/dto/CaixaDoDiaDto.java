package com.barbearia.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CaixaDoDiaDto(
        LocalDate data,
        BigDecimal totalGeral,
        List<TotalPorFormaPagamentoDto> porFormaPagamento,
        List<TotalPorProfissionalDto> porProfissional) {
}
