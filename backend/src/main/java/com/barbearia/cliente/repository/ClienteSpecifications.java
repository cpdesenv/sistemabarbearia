package com.barbearia.cliente.repository;

import org.springframework.data.jpa.domain.Specification;

import com.barbearia.cliente.domain.Cliente;

public final class ClienteSpecifications {

    private ClienteSpecifications() {
    }

    public static Specification<Cliente> comFiltros(String busca) {
        return (root, query, builder) -> {
            if (busca == null || busca.isBlank()) {
                return builder.conjunction();
            }

            String termo = busca.trim();
            String termoDigitos = termo.replaceAll("\\D", "");
            String termoLike = "%" + termo.toLowerCase() + "%";

            var porNome = builder.like(builder.lower(root.get("nome")), termoLike);
            var porTelefone = builder.like(root.get("telefone"), "%" + termo + "%");

            if (!termoDigitos.isBlank()) {
                var porCpf = builder.like(root.get("cpf"), "%" + termoDigitos + "%");
                var porTelefoneDigitos = builder.like(root.get("telefone"), "%" + termoDigitos + "%");
                return builder.or(porNome, porTelefone, porTelefoneDigitos, porCpf);
            }

            return builder.or(porNome, porTelefone);
        };
    }
}
