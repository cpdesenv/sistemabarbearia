package com.barbearia.financeiro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Despesa avulsa (aluguel, contas, compras nao ligadas a produto revendido).
 * Lancamento e' definitivo — nao ha edicao/exclusao nesta fase. Reduz o
 * caixa em maos no calculo de {@code FluxoCaixaService}.
 */
@Entity
@Table(name = "despesa")
@Getter
@Setter
@NoArgsConstructor
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @Column(nullable = false)
    private LocalDate data;

    private String categoria;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    @Column(name = "comprovante_url")
    private String comprovanteUrl;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
