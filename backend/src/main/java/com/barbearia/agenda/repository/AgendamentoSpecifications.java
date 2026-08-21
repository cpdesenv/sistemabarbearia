package com.barbearia.agenda.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.agenda.domain.Agendamento;
import com.barbearia.agenda.domain.StatusAgendamento;

public final class AgendamentoSpecifications {

    private AgendamentoSpecifications() {
    }

    public static Specification<Agendamento> comFiltros(Instant de, Instant ate, UUID profissionalUuid,
            UUID clienteUuid, StatusAgendamento status) {
        return (root, query, builder) -> {
            var predicado = builder.conjunction();

            if (de != null) {
                predicado = builder.and(predicado, builder.greaterThanOrEqualTo(root.get("fim"), de));
            }
            if (ate != null) {
                predicado = builder.and(predicado, builder.lessThanOrEqualTo(root.get("inicio"), ate));
            }
            if (profissionalUuid != null) {
                predicado = builder.and(predicado,
                        builder.equal(root.get("profissional").get("uuidPublico"), profissionalUuid));
            }
            if (clienteUuid != null) {
                predicado = builder.and(predicado,
                        builder.equal(root.get("cliente").get("uuidPublico"), clienteUuid));
            }
            if (status != null) {
                predicado = builder.and(predicado, builder.equal(root.get("status"), status));
            }

            return predicado;
        };
    }
}
