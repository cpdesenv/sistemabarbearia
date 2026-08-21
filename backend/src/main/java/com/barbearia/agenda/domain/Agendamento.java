package com.barbearia.agenda.domain;

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

import com.barbearia.cliente.domain.Cliente;
import com.barbearia.profissional.domain.Profissional;

/**
 * Agendamento de um ou mais servicos, para um cliente, com um profissional.
 * Nunca deletado fisicamente — cancelamento e' soft delete (status
 * CANCELADO + motivoCancelamento). A protecao definitiva contra dois
 * agendamentos sobrepostos para o mesmo profissional e' a constraint de
 * exclusao {@code agendamento_sem_sobreposicao} no banco (ver
 * V16__cria_tabela_agendamento.sql) — a validacao em Java (AvailabilityService)
 * so' evita a maioria dos casos e da' uma mensagem legivel; quem impede a
 * corrida de concorrencia de verdade e' o Postgres.
 */
@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemAgendamento origem;

    @Column(name = "google_event_id")
    private String googleEventId;

    private String observacao;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @Column(name = "usuario_criador_id")
    private Long usuarioCriadorId;

    @OneToMany(mappedBy = "agendamento", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<AgendamentoServico> servicos = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public void adicionarServico(AgendamentoServico item) {
        item.setAgendamento(this);
        servicos.add(item);
    }

    public void limparServicos() {
        servicos.clear();
    }
}
