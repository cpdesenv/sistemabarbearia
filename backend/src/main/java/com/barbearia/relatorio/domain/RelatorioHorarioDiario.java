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

import com.barbearia.relatorio.dto.AgregacaoHorarioDto;

/**
 * Fato pre-agregado (Fase 11): quantidade de agendamentos FINALIZADO por dia
 * x hora — base do heatmap de horarios de maior movimento. So existe linha
 * para horas com pelo menos um atendimento (tabela esparsa); dia da semana e'
 * derivado de {@code data} na leitura (ver {@code RelatorioHeatmapService}),
 * nao armazenado, para o mesmo relatorio servir qualquer intervalo de datas.
 */
@Entity
@Table(name = "relatorio_horario_diario")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioHorarioDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private int hora;

    @Column(name = "quantidade_finalizados", nullable = false)
    private int quantidadeFinalizados;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RelatorioHorarioDiario(LocalDate data, AgregacaoHorarioDto agregacao) {
        this.data = data;
        this.hora = agregacao.hora();
        this.quantidadeFinalizados = (int) agregacao.quantidadeFinalizados();
    }
}
