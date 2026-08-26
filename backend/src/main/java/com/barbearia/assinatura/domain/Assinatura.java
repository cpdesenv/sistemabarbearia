package com.barbearia.assinatura.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

import com.barbearia.cliente.domain.Cliente;

/**
 * Assinatura de um cliente a um {@link PlanoAssinatura}. So' pode existir uma
 * assinatura ATIVA ou INADIMPLENTE por cliente por vez (indice unico parcial
 * {@code idx_assinatura_cliente_em_curso} — ver
 * V22__cria_tabelas_plano_assinatura_e_assinatura.sql).
 *
 * <p>{@code saldoCortesAtual} e' ajustado por UPDATE atomico
 * ({@code AssinaturaRepository#ajustarSaldo}), no mesmo padrao de
 * {@code ProdutoRepository#ajustarEstoque}, para que dois agendamentos
 * simultaneos do mesmo cliente nunca consumam saldo em duplicidade.
 */
@Entity
@Table(name = "assinatura")
@Getter
@Setter
@NoArgsConstructor
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_assinatura_id", nullable = false)
    private PlanoAssinatura plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status = StatusAssinatura.ATIVA;

    @Column(name = "saldo_cortes_atual", nullable = false)
    private int saldoCortesAtual;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_proxima_renovacao", nullable = false)
    private LocalDate dataProximaRenovacao;

    @Column(name = "data_cancelamento")
    private LocalDate dataCancelamento;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
