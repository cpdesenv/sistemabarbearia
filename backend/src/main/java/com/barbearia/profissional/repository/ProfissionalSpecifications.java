package com.barbearia.profissional.repository;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.profissional.domain.Profissional;

public final class ProfissionalSpecifications {

    private ProfissionalSpecifications() {
    }

    public static Specification<Profissional> comFiltros(String nome, Boolean ativo) {
        return (root, query, builder) -> {
            var predicado = builder.conjunction();

            if (nome != null && !nome.isBlank()) {
                predicado = builder.and(predicado,
                        builder.like(builder.lower(root.get("nome")), "%" + nome.toLowerCase() + "%"));
            }
            if (ativo != null) {
                predicado = builder.and(predicado, builder.equal(root.get("ativo"), ativo));
            }

            return predicado;
        };
    }
}
