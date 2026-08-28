package com.barbearia.ia.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro unico (singleton, id fixo em {@link #ID_SINGLETON}) da
 * configuracao do agente de IA de atendimento. {@link #ativo} e o kill
 * switch obrigatorio do PRD (Fase 10): quando desligado, o
 * {@code AgenteAtendimentoService} joga toda conversa em modo HUMANO sem
 * chamar o {@code AiAgentGateway}.
 */
@Entity
@Table(name = "configuracao_ia")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoIa {

    public static final long ID_SINGLETON = 1L;

    @Id
    private Long id = ID_SINGLETON;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "limite_turnos", nullable = false)
    private int limiteTurnos = 25;

    @Column(name = "teto_custo_mensal_centavos", nullable = false)
    private long tetoCustoMensalCentavos = 10_000;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
