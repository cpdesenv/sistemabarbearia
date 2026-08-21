package com.barbearia.produto.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma movimentacao de estoque (entrada, saida, ajuste ou devolucao).
 * {@code quantidade} e' um delta com sinal — positivo para ENTRADA/DEVOLUCAO/
 * ajuste-positivo, negativo para SAIDA/ajuste-negativo — de forma que somar
 * o historico inteiro de um produto reproduz {@link Produto#getEstoqueAtual()}.
 *
 * <p>{@code comandaId} e {@code usuarioId} sao referencias cruas (sem
 * relacao JPA), do mesmo jeito que {@code Agendamento#usuarioCriadorId} —
 * evita acoplar o modulo produto ao modulo financeiro.
 */
@Entity
@Table(name = "movimento_estoque")
@Getter
@Setter
@NoArgsConstructor
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentoEstoque tipo;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "custo_unitario")
    private BigDecimal custoUnitario;

    private String motivo;

    @Column(name = "comanda_id")
    private Long comandaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public MovimentoEstoque(Produto produto, TipoMovimentoEstoque tipo, int quantidade, BigDecimal custoUnitario,
            String motivo, Long comandaId, Long usuarioId) {
        this.produto = produto;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.custoUnitario = custoUnitario;
        this.motivo = motivo;
        this.comandaId = comandaId;
        this.usuarioId = usuarioId;
    }
}
