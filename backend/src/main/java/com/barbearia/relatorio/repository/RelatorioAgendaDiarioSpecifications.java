package com.barbearia.relatorio.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.relatorio.domain.RelatorioAgendaDiario;

public final class RelatorioAgendaDiarioSpecifications {

    private RelatorioAgendaDiarioSpecifications() {
    }

    public static Specification<RelatorioAgendaDiario> comFiltros(LocalDate dataInicial, LocalDate dataFinal,
            Long profissionalId) {
        return (root, query, builder) -> {
            var predicado = builder.and(
                    builder.greaterThanOrEqualTo(root.get("data"), dataInicial),
                    builder.lessThanOrEqualTo(root.get("data"), dataFinal));

            if (profissionalId != null) {
                predicado = builder.and(predicado, builder.equal(root.get("profissionalId"), profissionalId));
            }

            return predicado;
        };
    }
}
