package com.barbearia.relatorio.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.relatorio.domain.RelatorioServicoDiario;

public final class RelatorioServicoDiarioSpecifications {

    private RelatorioServicoDiarioSpecifications() {
    }

    public static Specification<RelatorioServicoDiario> comFiltros(LocalDate dataInicial, LocalDate dataFinal,
            Long profissionalId, Long servicoId, FormaPagamento formaPagamento) {
        return (root, query, builder) -> {
            var predicado = builder.and(
                    builder.greaterThanOrEqualTo(root.get("data"), dataInicial),
                    builder.lessThanOrEqualTo(root.get("data"), dataFinal));

            if (profissionalId != null) {
                predicado = builder.and(predicado, builder.equal(root.get("profissionalId"), profissionalId));
            }
            if (servicoId != null) {
                predicado = builder.and(predicado, builder.equal(root.get("servicoId"), servicoId));
            }
            if (formaPagamento != null) {
                predicado = builder.and(predicado, builder.equal(root.get("formaPagamento"), formaPagamento));
            }

            return predicado;
        };
    }
}
