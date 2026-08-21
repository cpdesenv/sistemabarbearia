package com.barbearia.financeiro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.barbearia.agenda.domain.Agendamento;

/**
 * Comanda de atendimento: itens, desconto, forma de pagamento e fechamento.
 * Sempre vinculada a um {@link Agendamento}. So' pode existir uma comanda
 * ABERTA por vez por agendamento (garantido pelo indice unico parcial
 * {@code idx_comanda_agendamento_aberta} — ver V18__cria_tabela_comanda.sql);
 * o historico de comandas FECHADA/ESTORNADA do mesmo agendamento e'
 * preservado, nunca sobrescrito.
 *
 * <p>Comanda FECHADA e' imutavel: qualquer correcao e' feita por estorno
 * (status ESTORNADA, com motivo e auditoria) seguido da abertura de uma nova
 * comanda para o mesmo agendamento — nunca editando os itens de uma comanda
 * ja fechada.
 */
@Entity
@Table(name = "comanda")
@Getter
@Setter
@NoArgsConstructor
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComanda status = StatusComanda.ABERTA;

    @Column(name = "desconto_valor", nullable = false)
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(name = "desconto_motivo")
    private String descontoMotivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "fechada_em")
    private Instant fechadaEm;

    @Column(name = "fechada_por_usuario_id")
    private Long fechadaPorUsuarioId;

    @Column(name = "estornada_em")
    private Instant estornadaEm;

    @Column(name = "estornada_por_usuario_id")
    private Long estornadaPorUsuarioId;

    @Column(name = "motivo_estorno")
    private String motivoEstorno;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<ComandaItem> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public void adicionarItem(ComandaItem item) {
        item.setComanda(this);
        itens.add(item);
    }

    public void removerItem(ComandaItem item) {
        itens.remove(item);
    }
}
