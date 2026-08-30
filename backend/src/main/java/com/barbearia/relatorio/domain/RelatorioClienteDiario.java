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

/**
 * Fato pre-agregado (Fase 11): clientes novos vs. recorrentes por dia — uma
 * propriedade do cliente, sem dimensao de profissional/servico. "Novo" =
 * nenhuma comanda FECHADA do cliente antes do inicio deste dia (ver
 * RelatorioAgregacaoService).
 */
@Entity
@Table(name = "relatorio_cliente_diario")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioClienteDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @Column(name = "clientes_novos", nullable = false)
    private int clientesNovos;

    @Column(name = "clientes_recorrentes", nullable = false)
    private int clientesRecorrentes;

    @Column(name = "atendimentos_totais", nullable = false)
    private int atendimentosTotais;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public RelatorioClienteDiario(LocalDate data, int clientesNovos, int clientesRecorrentes,
            int atendimentosTotais) {
        this.data = data;
        this.clientesNovos = clientesNovos;
        this.clientesRecorrentes = clientesRecorrentes;
        this.atendimentosTotais = atendimentosTotais;
    }
}
