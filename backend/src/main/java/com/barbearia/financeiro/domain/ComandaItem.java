package com.barbearia.financeiro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.produto.domain.Produto;
import com.barbearia.servico.domain.Servico;

/**
 * Um item incluido numa comanda: servico ou produto (nunca os dois, ver
 * {@link #tipo}). {@code descricao} e {@code valorUnitario} sao um snapshot
 * do servico/produto no momento em que o item foi adicionado — mudar o preco
 * depois nao deve alterar comandas ja fechadas nem itens ja lancados.
 *
 * <p>Itens de produto nao geram comissao (so' servico gera — ver
 * {@code ComandaService#recalcularTotais}); a baixa/devolucao de estoque de
 * um item de produto so' acontece no fechamento/estorno da comanda, nunca ao
 * simplesmente adicionar/remover o item (ver {@code ComandaService#fechar}/
 * {@code #estornar} e {@code EstoqueService}).
 *
 * <p>Um item de servico coberto pelo saldo de uma {@link Assinatura} (ver
 * {@code AssinaturaService#tentarConsumirSaldo}) nasce com {@link #assinatura}
 * preenchida e valor zerado — ja foi pago via mensalidade, entao nao soma no
 * caixa nem gera comissao desta comanda.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoItemComanda tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assinatura_id")
    private Assinatura assinatura;

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
        this.tipo = TipoItemComanda.SERVICO;
        this.servico = servico;
        this.descricao = descricao;
        this.quantidade = 1;
        this.valorUnitario = valorUnitario;
        this.valorBruto = valorUnitario;
        this.valorLiquido = valorUnitario;
    }

    public ComandaItem(Produto produto, String descricao, BigDecimal valorUnitario) {
        this.tipo = TipoItemComanda.PRODUTO;
        this.produto = produto;
        this.descricao = descricao;
        this.quantidade = 1;
        this.valorUnitario = valorUnitario;
        this.valorBruto = valorUnitario;
        this.valorLiquido = valorUnitario;
    }

    /** Item de servico coberto pelo saldo de uma assinatura — ver {@code AssinaturaService#tentarConsumirSaldo}. */
    public ComandaItem(Servico servico, String descricao, Assinatura assinatura) {
        this.tipo = TipoItemComanda.SERVICO;
        this.servico = servico;
        this.assinatura = assinatura;
        this.descricao = descricao;
        this.quantidade = 1;
        this.valorUnitario = BigDecimal.ZERO;
        this.valorBruto = BigDecimal.ZERO;
        this.valorLiquido = BigDecimal.ZERO;
    }

    public boolean isCobertoPorAssinatura() {
        return assinatura != null;
    }
}
