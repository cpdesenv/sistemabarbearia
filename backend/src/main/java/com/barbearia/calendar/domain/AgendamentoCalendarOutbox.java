package com.barbearia.calendar.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.barbearia.agenda.domain.Agendamento;

/**
 * Fila de sincronizacao com o Google Calendar (padrao outbox transacional):
 * gravada na mesma transacao que confirma/remarca/cancela o agendamento (ver
 * {@code AgendamentoService}), e processada depois por
 * {@code CalendarOutboxWorker}. No maximo uma linha {@code PENDENTE} por
 * agendamento (constraint {@code idx_outbox_agendamento_pendente} no banco).
 */
@Entity
@Table(name = "agendamento_calendar_outbox")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoCalendarOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false)
    private TipoOperacaoOutbox tipoOperacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusOutbox status = StatusOutbox.PENDENTE;

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
