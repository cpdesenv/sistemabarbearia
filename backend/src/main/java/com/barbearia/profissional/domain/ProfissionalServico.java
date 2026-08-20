package com.barbearia.profissional.domain;

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
 * Vinculo N:N entre {@link Profissional} e {@link Servico}. Uma linha por
 * par existente representa "este profissional atende este servico";
 * {@code comissaoPercentual} nula significa que vale a comissao padrao do
 * profissional (ver {@link Profissional#getComissaoPercentualPadrao()}).
 */
@Entity
@Table(name = "profissional_servico")
@Getter
@Setter
@NoArgsConstructor
public class ProfissionalServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "comissao_percentual")
    private BigDecimal comissaoPercentual;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public ProfissionalServico(Profissional profissional, Servico servico, BigDecimal comissaoPercentual) {
        this.profissional = profissional;
        this.servico = servico;
        this.comissaoPercentual = comissaoPercentual;
    }
}
