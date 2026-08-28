package com.barbearia.ia.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import com.barbearia.mensageria.domain.Conversa;

/**
 * Uma linha por chamada ao {@code AiAgentGateway} (nao por conversa) — a
 * base do "custo acumulado de LLM" exibido no painel e do teto de custo
 * mensal (guardrails obrigatorios do PRD, Fase 10).
 */
@Entity
@Table(name = "uso_llm")
@Getter
@Setter
@NoArgsConstructor
public class UsoLlm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private Conversa conversa;

    @Column(nullable = false)
    private String modelo;

    @Column(name = "tokens_entrada", nullable = false)
    private int tokensEntrada;

    @Column(name = "tokens_saida", nullable = false)
    private int tokensSaida;

    @Column(name = "custo_centavos", nullable = false)
    private BigDecimal custoCentavos;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
