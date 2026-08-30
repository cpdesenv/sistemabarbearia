package com.barbearia.relatorio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

import com.barbearia.relatorio.dto.AgregacaoProdutoDto;

/**
 * Fato pre-agregado (Fase 11): quantidade/receita/custo de produtos vendidos
 * por dia. Povoada exclusivamente por {@code RelatorioAgregacaoService} (job
 * noturno ou reprocessamento) — nunca por um CRUD. {@code custoTotal} usa o
 * preco de custo do produto no momento da agregacao (ver comentario da
 * migration V33), nao um snapshot por venda.
 */
@Entity
@Table(name = "relatorio_produto_diario")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioProdutoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "produto_nome", nullable = false)
    private String produtoNome;

    @Column(name = "quantidade_vendida", nullable = false)
    private int quantidadeVendida;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "custo_total", nullable = false)
    private BigDecimal custoTotal;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RelatorioProdutoDiario(LocalDate data, AgregacaoProdutoDto agregacao) {
        this.data = data;
        this.produtoId = agregacao.produtoId();
        this.produtoNome = agregacao.produtoNome();
        this.quantidadeVendida = (int) agregacao.quantidadeVendida();
        this.valorTotal = agregacao.valorTotal();
        this.custoTotal = agregacao.custoTotal();
    }
}
