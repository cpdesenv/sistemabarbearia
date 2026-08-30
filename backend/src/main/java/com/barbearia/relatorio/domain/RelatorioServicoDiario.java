package com.barbearia.relatorio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.barbearia.financeiro.domain.FormaPagamento;
import com.barbearia.relatorio.dto.AgregacaoServicoDto;

/**
 * Fato pre-agregado (Fase 11): faturamento/comissao/quantidade por dia x
 * profissional x servico x forma de pagamento. Povoada exclusivamente por
 * {@code RelatorioAgregacaoService} (job noturno ou reprocessamento) — nunca
 * por um CRUD. {@code profissionalId}/{@code servicoId} sao ids internos
 * simples (sem relacionamento JPA): esta e' uma tabela analitica, os nomes
 * ja vem denormalizados (snapshot, mesmo padrao de
 * {@code ComandaItem#descricao}) para o relatorio nunca precisar de join em
 * tempo de consulta.
 */
@Entity
@Table(name = "relatorio_servico_diario")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioServicoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "profissional_nome", nullable = false)
    private String profissionalNome;

    @Column(name = "servico_id", nullable = false)
    private Long servicoId;

    @Column(name = "servico_nome", nullable = false)
    private String servicoNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "comissao_total", nullable = false)
    private BigDecimal comissaoTotal;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RelatorioServicoDiario(LocalDate data, AgregacaoServicoDto agregacao) {
        this.data = data;
        this.profissionalId = agregacao.profissionalId();
        this.profissionalNome = agregacao.profissionalNome();
        this.servicoId = agregacao.servicoId();
        this.servicoNome = agregacao.servicoNome();
        this.formaPagamento = agregacao.formaPagamento();
        this.quantidade = (int) agregacao.quantidade();
        this.valorTotal = agregacao.valorTotal();
        this.comissaoTotal = agregacao.comissaoTotal();
    }
}
