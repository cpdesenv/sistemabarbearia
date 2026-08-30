package com.barbearia.relatorio.domain;

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

import com.barbearia.relatorio.dto.AgregacaoAgendaDto;

/**
 * Fato pre-agregado (Fase 11): cancelamentos, faltas e ocupacao por dia x
 * profissional. Uma linha por profissional ativo por dia, mesmo sem nenhum
 * agendamento naquele dia — necessario para o denominador de ocupacao
 * (minutos de capacidade) nao ficar subestimado ao somar varios dias.
 */
@Entity
@Table(name = "relatorio_agenda_diario")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioAgendaDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "profissional_nome", nullable = false)
    private String profissionalNome;

    @Column(name = "quantidade_finalizados", nullable = false)
    private int quantidadeFinalizados;

    @Column(name = "quantidade_cancelados", nullable = false)
    private int quantidadeCancelados;

    @Column(name = "quantidade_nao_compareceu", nullable = false)
    private int quantidadeNaoCompareceu;

    @Column(name = "minutos_capacidade", nullable = false)
    private int minutosCapacidade;

    @Column(name = "minutos_ocupados", nullable = false)
    private int minutosOcupados;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RelatorioAgendaDiario(LocalDate data, AgregacaoAgendaDto agregacao) {
        this.data = data;
        this.profissionalId = agregacao.profissionalId();
        this.profissionalNome = agregacao.profissionalNome();
        this.quantidadeFinalizados = agregacao.quantidadeFinalizados();
        this.quantidadeCancelados = agregacao.quantidadeCancelados();
        this.quantidadeNaoCompareceu = agregacao.quantidadeNaoCompareceu();
        this.minutosCapacidade = agregacao.minutosCapacidade();
        this.minutosOcupados = agregacao.minutosOcupados();
    }
}
