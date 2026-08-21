package com.barbearia.produto.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.produto.domain.MovimentoEstoque;
import com.barbearia.produto.domain.Produto;
import com.barbearia.produto.domain.TipoMovimentoEstoque;
import com.barbearia.produto.dto.MovimentoEstoqueDto;
import com.barbearia.produto.dto.ProdutoDto;
import com.barbearia.produto.dto.ProdutoMapper;
import com.barbearia.produto.repository.MovimentoEstoqueRepository;
import com.barbearia.produto.repository.ProdutoRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Toda a logica de movimentacao de estoque: entrada (compra), ajuste manual
 * de inventario, e a baixa/devolucao automatica disparada pelo fechamento e
 * pelo estorno de uma comanda (ver {@code ComandaService}). O CRUD de
 * catalogo (nome, preco, categoria...) fica em {@link ProdutoService}.
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final ProdutoMapper produtoMapper;
    private final AuditoriaService auditoriaService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public ProdutoDto registrarEntrada(UUID produtoUuid, Integer quantidade, BigDecimal custoUnitario, String motivo,
            Long usuarioId, HttpServletRequest httpRequest) {
        if (quantidade == null || quantidade <= 0) {
            throw new NegocioException("A quantidade da entrada deve ser maior que zero.");
        }
        Produto produto = buscarPorUuid(produtoUuid);

        produtoRepository.ajustarEstoque(produto.getId(), quantidade);
        entityManager.refresh(produto);

        movimentoEstoqueRepository.save(
                new MovimentoEstoque(produto, TipoMovimentoEstoque.ENTRADA, quantidade, custoUnitario, motivo, null,
                        usuarioId));

        auditoriaService.registrar(usuarioId, "ESTOQUE_ENTRADA", "produto", produto.getId(),
                "Entrada de " + quantidade + " unidade(s) de '" + produto.getNome() + "'"
                        + (custoUnitario != null ? " a R$ " + custoUnitario + "/un" : ""),
                httpRequest);

        return produtoMapper.paraDto(produto);
    }

    @Transactional
    public ProdutoDto registrarAjuste(UUID produtoUuid, Integer novaQuantidadeContada, String motivo, Long usuarioId,
            HttpServletRequest httpRequest) {
        if (motivo == null || motivo.isBlank()) {
            throw new NegocioException("Informe o motivo do ajuste de estoque.");
        }
        if (novaQuantidadeContada == null || novaQuantidadeContada < 0) {
            throw new NegocioException("A quantidade contada nao pode ser negativa.");
        }
        Produto produto = buscarPorUuid(produtoUuid);
        int delta = novaQuantidadeContada - produto.getEstoqueAtual();
        if (delta == 0) {
            return produtoMapper.paraDto(produto);
        }

        produtoRepository.ajustarEstoque(produto.getId(), delta);
        entityManager.refresh(produto);

        movimentoEstoqueRepository.save(
                new MovimentoEstoque(produto, TipoMovimentoEstoque.AJUSTE, delta, null, motivo, null, usuarioId));

        auditoriaService.registrar(usuarioId, "ESTOQUE_AJUSTADO", "produto", produto.getId(),
                "Estoque de '" + produto.getNome() + "' ajustado em " + (delta > 0 ? "+" : "") + delta
                        + " (motivo: " + motivo + ")",
                httpRequest);

        return produtoMapper.paraDto(produto);
    }

    /** Chamado por {@code ComandaService.fechar()} para cada item de produto da comanda. */
    @Transactional
    public void baixarPorComanda(Produto produto, int quantidade, Long comandaId, Long usuarioId,
            HttpServletRequest httpRequest) {
        int linhasAfetadas = produtoRepository.ajustarEstoque(produto.getId(), -quantidade);
        if (linhasAfetadas == 0) {
            Produto atual = produtoRepository.findById(produto.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
            throw new NegocioException("Estoque insuficiente de '" + atual.getNome()
                    + "' para fechar a comanda (disponivel: " + atual.getEstoqueAtual() + ").");
        }
        entityManager.refresh(produto);

        movimentoEstoqueRepository.save(
                new MovimentoEstoque(produto, TipoMovimentoEstoque.SAIDA, -quantidade, null, null, comandaId,
                        usuarioId));

        auditoriaService.registrar(usuarioId, "ESTOQUE_BAIXA_COMANDA", "produto", produto.getId(),
                "Baixa de " + quantidade + " unidade(s) de '" + produto.getNome() + "' pela comanda #" + comandaId,
                httpRequest);
    }

    /** Chamado por {@code ComandaService.estornar()} para cada item de produto da comanda. */
    @Transactional
    public void devolverPorComanda(Produto produto, int quantidade, Long comandaId, Long usuarioId,
            HttpServletRequest httpRequest) {
        produtoRepository.ajustarEstoque(produto.getId(), quantidade);
        entityManager.refresh(produto);

        movimentoEstoqueRepository.save(
                new MovimentoEstoque(produto, TipoMovimentoEstoque.DEVOLUCAO, quantidade, null, null, comandaId,
                        usuarioId));

        auditoriaService.registrar(usuarioId, "ESTOQUE_DEVOLUCAO_ESTORNO", "produto", produto.getId(),
                "Devolucao de " + quantidade + " unidade(s) de '" + produto.getNome()
                        + "' pelo estorno da comanda #" + comandaId,
                httpRequest);
    }

    @Transactional(readOnly = true)
    public Page<MovimentoEstoqueDto> extrato(UUID produtoUuid, Pageable pageable) {
        Produto produto = buscarPorUuid(produtoUuid);
        return movimentoEstoqueRepository.findByProdutoOrderByCriadoEmDesc(produto, pageable)
                .map(this::paraDto);
    }

    private MovimentoEstoqueDto paraDto(MovimentoEstoque movimento) {
        return new MovimentoEstoqueDto(movimento.getTipo(), movimento.getQuantidade(), movimento.getCustoUnitario(),
                movimento.getMotivo(), movimento.getComandaId(), movimento.getCriadoEm());
    }

    private Produto buscarPorUuid(UUID uuid) {
        return produtoRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
    }
}
