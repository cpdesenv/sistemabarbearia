package com.barbearia.agenda.domain;

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

import com.barbearia.servico.domain.Servico;

/**
 * Um servico incluido num agendamento. {@code duracaoMinutos} e {@code preco}
 * sao um snapshot do {@link Servico} no momento do agendamento — mudar o
 * preco ou a duracao de um servico depois nao deve alterar agendamentos ja
 * criados.
 */
@Entity
@Table(name = "agendamento_servico")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    @Column(nullable = false)
    private BigDecimal preco;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public AgendamentoServico(Servico servico, int duracaoMinutos, BigDecimal preco) {
        this.servico = servico;
        this.duracaoMinutos = duracaoMinutos;
        this.preco = preco;
    }
}
