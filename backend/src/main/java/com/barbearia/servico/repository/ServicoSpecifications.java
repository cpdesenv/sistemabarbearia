package com.barbearia.servico.repository;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.servico.domain.Servico;

public final class ServicoSpecifications {

    private ServicoSpecifications() {
    }

    public static Specification<Servico> comFiltros(String nome, String categoria, Boolean ativo) {
        return (root, query, builder) -> {
            var predicado = builder.conjunction();

            if (nome != null && !nome.isBlank()) {
                predicado = builder.and(predicado,
                        builder.like(builder.lower(root.get("nome")), "%" + nome.toLowerCase() + "%"));
            }
            if (categoria != null && !categoria.isBlank()) {
                predicado = builder.and(predicado,
                        builder.equal(builder.lower(root.get("categoria")), categoria.toLowerCase()));
            }
            if (ativo != null) {
                predicado = builder.and(predicado, builder.equal(root.get("ativo"), ativo));
            }

            return predicado;
        };
    }
}
