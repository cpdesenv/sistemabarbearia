package com.barbearia.financeiro.dto;

import java.math.BigDecimal;

import com.barbearia.financeiro.domain.FormaPagamento;

public record TotalPorFormaPagamentoDto(FormaPagamento formaPagamento, BigDecimal total) {
}
