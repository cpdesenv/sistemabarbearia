package com.barbearia.financeiro.domain;

import java.math.BigDecimal;
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

import com.barbearia.servico.domain.Servico;

/**
 * Um item (servico) incluido numa comanda. {@code descricao} e
 * {@code valorUnitario} sao um snapshot do {@link Servico} no momento em que
 * o item foi adicionado — mudar o preco do servico depois nao deve alterar
 * comandas ja fechadas nem itens ja lancados. Nesta sub-entrega (5A) todo
 * item e' um servico; produtos entram na sub-entrega 5B.
 */
@Entity
@Table(name = "comanda_item")
@Getter
@Setter
@NoArgsConstructor
public class ComandaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, updatable = false)
    private UUID uuidPublico = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comanda_id", nullable = false)
    private Comanda comanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private int quantidade = 1;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_bruto", nullable = false)
    private BigDecimal valorBruto;

    @Column(name = "valor_desconto_rateado", nullable = false)
    private BigDecimal valorDescontoRateado = BigDecimal.ZERO;

    @Column(name = "valor_liquido", nullable = false)
    private BigDecimal valorLiquido;

    @Column(name = "comissao_percentual_aplicado")
    private BigDecimal comissaoPercentualAplicado;

    @Column(name = "comissao_valor")
    private BigDecimal comissaoValor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public ComandaItem(Servico servico, String descricao, BigDecimal valorUnitario) {
        this.servico = servico;
        this.descricao = descricao;
        this.quantidade = 1;
        this.valorUnitario = valorUnitario;
        this.valorBruto = valorUnitario;
        this.valorLiquido = valorUnitario;
    }
}
