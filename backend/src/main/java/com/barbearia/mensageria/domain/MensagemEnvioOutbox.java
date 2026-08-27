package com.barbearia.mensageria.domain;

import java.time.Instant;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila de envio (padrao outbox transacional, mesmo estilo de
 * {@code AgendamentoCalendarOutbox} da Fase 8): nasce na mesma transacao que
 * a {@link Mensagem} SAIDA, e e processada depois por
 * {@code MensagemEnvioOutboxWorker}.
 */
@Entity
@Table(name = "mensagem_envio_outbox")
@Getter
@Setter
@NoArgsConstructor
public class MensagemEnvioOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mensagem_id", nullable = false, unique = true)
    private Mensagem mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEnvioOutbox status = StatusEnvioOutbox.PENDENTE;

    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "proxima_tentativa_em", nullable = false)
    private Instant proximaTentativaEm = Instant.now();

    @Column(name = "ultimo_erro")
    private String ultimoErro;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
