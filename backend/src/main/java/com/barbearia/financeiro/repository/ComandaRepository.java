package com.barbearia.financeiro.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.dashboard.dto.ItemContagemDto;
import com.barbearia.financeiro.domain.Comanda;
import com.barbearia.financeiro.domain.StatusComanda;
import com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto;
import com.barbearia.relatorio.dto.AgregacaoProdutoDto;
import com.barbearia.relatorio.dto.AgregacaoServicoDto;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    Optional<Comanda> findByUuidPublico(UUID uuidPublico);

    Optional<Comanda> findByAgendamento_UuidPublicoAndStatus(UUID agendamentoUuidPublico, StatusComanda status);

    List<Comanda> findByAgendamento_UuidPublicoOrderByCriadoEmDesc(UUID agendamentoUuidPublico);

    List<Comanda> findByAgendamento_Cliente_UuidPublicoOrderByCriadoEmDesc(UUID clienteUuidPublico);

    List<Comanda> findByStatusAndFechadaEmBetween(StatusComanda status, Instant inicio, Instant fim);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM Comanda c WHERE c.status = :status")
    BigDecimal somarValorTotalPorStatus(@Param("status") StatusComanda status);

    @Query("SELECT COALESCE(SUM(c.valorTotal), 0) FROM Comanda c "
            + "WHERE c.status = :status AND c.fechadaEm BETWEEN :inicio AND :fim")
    BigDecimal somarValorTotalPorStatusEPeriodo(@Param("status") StatusComanda status, @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);

    @Query("SELECT COUNT(c) FROM Comanda c WHERE c.status = :status AND c.fechadaEm BETWEEN :inicio AND :fim")
    long contarPorStatusEPeriodo(@Param("status") StatusComanda status, @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);

    @Query("""
            SELECT new com.barbearia.financeiro.dto.TotalPorFormaPagamentoDto(c.formaPagamento, COALESCE(SUM(c.valorTotal), 0))
            FROM Comanda c
            WHERE c.status = :status AND c.fechadaEm BETWEEN :inicio AND :fim
            GROUP BY c.formaPagamento
            """)
    List<TotalPorFormaPagamentoDto> somarPorFormaPagamentoEPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("""
            SELECT new com.barbearia.dashboard.dto.ItemContagemDto(a.profissional.nome, COUNT(c))
            FROM Comanda c JOIN c.agendamento a
            WHERE c.status = :status AND c.fechadaEm BETWEEN :inicio AND :fim
            GROUP BY a.profissional.nome
            ORDER BY COUNT(c) DESC
            """)
    List<ItemContagemDto> contarAtendimentosPorProfissionalEPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("""
            SELECT new com.barbearia.dashboard.dto.ItemContagemDto(i.servico.nome, SUM(i.quantidade))
            FROM ComandaItem i
            WHERE i.tipo = com.barbearia.financeiro.domain.TipoItemComanda.SERVICO
              AND i.comanda.status = :status AND i.comanda.fechadaEm BETWEEN :inicio AND :fim
            GROUP BY i.servico.nome
            ORDER BY SUM(i.quantidade) DESC
            """)
    List<ItemContagemDto> somarServicosVendidosEPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim, Pageable paginacao);

    /**
     * Fonte de {@code AgregacaoServicoDto}: usada tanto pelo job noturno
     * (persiste o resultado em {@code RelatorioServicoDiario}) quanto pela
     * consulta ao vivo do dia corrente, ainda nao agregado — ver
     * {@code RelatorioFaturamentoService}.
     */
    @Query("""
            SELECT new com.barbearia.relatorio.dto.AgregacaoServicoDto(
                a.profissional.id, a.profissional.nome, i.servico.id, i.servico.nome,
                i.comanda.formaPagamento, SUM(i.quantidade), COALESCE(SUM(i.valorLiquido), 0),
                COALESCE(SUM(i.comissaoValor), 0))
            FROM ComandaItem i JOIN i.comanda.agendamento a
            WHERE i.tipo = com.barbearia.financeiro.domain.TipoItemComanda.SERVICO
              AND i.comanda.status = :status AND i.comanda.fechadaEm BETWEEN :inicio AND :fim
            GROUP BY a.profissional.id, a.profissional.nome, i.servico.id, i.servico.nome, i.comanda.formaPagamento
            """)
    List<AgregacaoServicoDto> agregarServicosPorPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Clientes distintos atendidos no periodo — base para separar novos de recorrentes em {@code RelatorioAgregacaoService}. */
    @Query("SELECT DISTINCT a.cliente.id FROM Comanda c JOIN c.agendamento a "
            + "WHERE c.status = :status AND c.fechadaEm BETWEEN :inicio AND :fim")
    List<Long> buscarClienteIdsAtendidosPorPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Um cliente e' "recorrente" num dia se ja tinha alguma comanda FECHADA antes do inicio daquele dia. */
    @Query("SELECT COUNT(c) > 0 FROM Comanda c JOIN c.agendamento a "
            + "WHERE c.status = :status AND a.cliente.id = :clienteId AND c.fechadaEm < :antes")
    boolean existeAtendimentoAnteriorDoCliente(@Param("status") StatusComanda status,
            @Param("clienteId") Long clienteId, @Param("antes") Instant antes);

    /**
     * Fonte de {@code AgregacaoProdutoDto}: usada tanto pelo job noturno
     * (persiste o resultado em {@code RelatorioProdutoDiario}) quanto pela
     * consulta ao vivo do dia corrente, ainda nao agregado — ver
     * {@code RelatorioProdutoService}. custo_total usa o preco de custo
     * ATUAL do produto (ver comentario da migration V33).
     */
    @Query("""
            SELECT new com.barbearia.relatorio.dto.AgregacaoProdutoDto(
                i.produto.id, i.produto.nome, SUM(i.quantidade), COALESCE(SUM(i.valorLiquido), 0),
                COALESCE(SUM(i.quantidade * i.produto.precoCusto), 0))
            FROM ComandaItem i
            WHERE i.tipo = com.barbearia.financeiro.domain.TipoItemComanda.PRODUTO
              AND i.comanda.status = :status AND i.comanda.fechadaEm BETWEEN :inicio AND :fim
            GROUP BY i.produto.id, i.produto.nome
            """)
    List<AgregacaoProdutoDto> agregarProdutosPorPeriodo(@Param("status") StatusComanda status,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
