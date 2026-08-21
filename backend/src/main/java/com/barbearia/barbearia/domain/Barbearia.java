package com.barbearia.barbearia.domain;

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
 * Registro unico de configuracao da barbearia (singleton, id fixo em
 * {@link #ID_SINGLETON}). Nunca criar nem listar via API — apenas ler e
 * editar (ver docs/limitacoes.md, secao "Sem multi-tenant").
 */
@Entity
@Table(name = "barbearia")
@Getter
@Setter
@NoArgsConstructor
public class Barbearia {

    public static final long ID_SINGLETON = 1L;

    @Id
    private Long id = ID_SINGLETON;

    @Column(nullable = false)
    private String nome;

    private String cnpj;
    private String telefone;
    private String email;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
    private String cep;

    @Column(name = "fuso_horario", nullable = false)
    private String fusoHorario;

    @Column(name = "antecedencia_minima_agendamento_minutos", nullable = false)
    private int antecedenciaMinimaAgendamentoMinutos;

    @Column(name = "antecedencia_maxima_agendamento_dias", nullable = false)
    private int antecedenciaMaximaAgendamentoDias;

    @Column(name = "antecedencia_minima_cancelamento_minutos", nullable = false)
    private int antecedenciaMinimaCancelamentoMinutos;

    @Column(name = "granularidade_slot_minutos", nullable = false)
    private int granularidadeSlotMinutos = 15;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
