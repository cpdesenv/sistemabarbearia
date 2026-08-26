package com.barbearia.assinatura.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.barbearia.servico.domain.Servico;

/**
 * Plano do Clube Cavalinho: preco mensal, quantidade de cortes inclusos por
 * ciclo (renovados a cada renovacao — ver {@code AssinaturaService}) e
 * desconto aplicado a servicos/produtos que ficarem fora do saldo. Apenas os
 * {@link #servicosInclusos} consomem saldo; qualquer outro servico e sempre
 * cobrado avulso, mesmo para um assinante ativo.
 */
@Entity
@Table(name = "plano_assinatura")
@Getter
@Setter
@NoArgsConstructor
public class PlanoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @Column(nullable = false)
    private String nome;

    private String descricao;

    @Column(name = "preco_mensal", nullable = false)
    private BigDecimal precoMensal;

    @Column(name = "cortes_incluidos_por_ciclo", nullable = false)
    private int cortesIncluidosPorCiclo;

    @Column(name = "percentual_desconto_adicional", nullable = false)
    private BigDecimal percentualDescontoAdicional = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "plano_assinatura_servico",
            joinColumns = @JoinColumn(name = "plano_assinatura_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicosInclusos = new HashSet<>();

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public boolean cobreServico(Servico servico) {
        return servicosInclusos.stream().anyMatch(s -> s.getId().equals(servico.getId()));
    }
}
