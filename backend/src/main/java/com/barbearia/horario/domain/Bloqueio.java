package com.barbearia.horario.domain;

import java.time.Instant;
import java.util.UUID;

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
 * Bloqueio pontual de agenda (ferias, atestado, feriado). Independente da
 * {@link JanelaHorario grade semanal}: pode se sobrepor a uma janela de
 * atendimento normal e prevalece sobre ela. {@code profissional} nulo
 * significa bloqueio global (fecha a barbearia inteira no periodo).
 */
@Entity
@Table(name = "bloqueio")
@Getter
@Setter
@NoArgsConstructor
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(nullable = false)
    private String motivo;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
