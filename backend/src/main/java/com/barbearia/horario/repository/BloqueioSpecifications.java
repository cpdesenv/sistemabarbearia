package com.barbearia.horario.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.horario.domain.Bloqueio;

public final class BloqueioSpecifications {

    private BloqueioSpecifications() {
    }

    public static Specification<Bloqueio> comFiltros(UUID profissionalUuid, Instant de, Instant ate) {
        return (root, query, builder) -> {
            var predicado = builder.conjunction();

            if (profissionalUuid != null) {
                predicado = builder.and(predicado,
                        builder.equal(root.get("profissional").get("uuidPublico"), profissionalUuid));
            }
            if (de != null) {
                predicado = builder.and(predicado, builder.greaterThanOrEqualTo(root.get("fim"), de));
            }
            if (ate != null) {
                predicado = builder.and(predicado, builder.lessThanOrEqualTo(root.get("inicio"), ate));
            }

            return predicado;
        };
    }
}
