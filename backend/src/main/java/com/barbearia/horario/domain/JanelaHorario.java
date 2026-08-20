package com.barbearia.horario.domain;

import java.time.Instant;
import java.time.LocalTime;

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

import com.barbearia.profissional.domain.Profissional;

/**
 * Uma janela de atendimento semanal recorrente de um profissional.
 * {@code diaSemana} segue {@link java.time.DayOfWeek#getValue()} (1=segunda
 * ... 7=domingo). Varias linhas no mesmo dia representam turnos diferentes
 * (ex.: manha e tarde).
 */
@Entity
@Table(name = "grade_horaria")
@Getter
@Setter
@NoArgsConstructor
public class JanelaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(name = "dia_semana", nullable = false)
    private int diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public JanelaHorario(Profissional profissional, int diaSemana, LocalTime horaInicio, LocalTime horaFim) {
        this.profissional = profissional;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }
}
