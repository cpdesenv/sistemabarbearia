package com.barbearia.produto.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.produto.domain.Produto;
import com.barbearia.produto.dto.AtualizarStatusProdutoRequest;
import com.barbearia.produto.dto.ProdutoDto;
import com.barbearia.produto.dto.ProdutoMapper;
import com.barbearia.produto.dto.SalvarProdutoRequest;
import com.barbearia.produto.repository.ProdutoRepository;
import com.barbearia.produto.repository.ProdutoSpecifications;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoMapper produtoMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<ProdutoDto> listar(String nome, String categoria, Boolean ativo, Pageable pageable) {
        return produtoRepository.findAll(ProdutoSpecifications.comFiltros(nome, categoria, ativo), pageable)
                .map(produtoMapper::paraDto);
    }

    @Transactional(readOnly = true)
    public ProdutoDto obter(UUID uuid) {
        return produtoMapper.paraDto(buscarPorUuid(uuid));
    }

    @Transactional(readOnly = true)
    public List<ProdutoDto> alertaEstoqueMinimo() {
        return produtoRepository.buscarAbaixoDoEstoqueMinimo().stream().map(produtoMapper::paraDto).toList();
    }

    @Transactional
    public ProdutoDto criar(SalvarProdutoRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Produto produto = new Produto();
        produtoMapper.copiarPara(requisicao, produto);
        normalizarCamposOpcionais(produto);
        produto = produtoRepository.save(produto);

        auditoriaService.registrar(usuarioId, "PRODUTO_CRIADO", "produto", produto.getId(),
                "Produto '" + produto.getNome() + "' criado", httpRequest);

        return produtoMapper.paraDto(produto);
    }

    @Transactional
    public ProdutoDto atualizar(UUID uuid, SalvarProdutoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Produto produto = buscarPorUuid(uuid);
        produtoMapper.copiarPara(requisicao, produto);
        normalizarCamposOpcionais(produto);
        produto = produtoRepository.save(produto);

        auditoriaService.registrar(usuarioId, "PRODUTO_ATUALIZADO", "produto", produto.getId(),
                "Produto '" + produto.getNome() + "' atualizado", httpRequest);

        return produtoMapper.paraDto(produto);
    }

    @Transactional
    public ProdutoDto atualizarStatus(UUID uuid, AtualizarStatusProdutoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Produto produto = buscarPorUuid(uuid);
        produto.setAtivo(requisicao.ativo());
        produto = produtoRepository.save(produto);

        String operacao = requisicao.ativo() ? "PRODUTO_ATIVADO" : "PRODUTO_DESATIVADO";
        auditoriaService.registrar(usuarioId, operacao, "produto", produto.getId(),
                "Produto '" + produto.getNome() + "' " + (requisicao.ativo() ? "ativado" : "desativado"),
                httpRequest);

        return produtoMapper.paraDto(produto);
    }

    private void normalizarCamposOpcionais(Produto produto) {
        if (produto.getUnidade() == null || produto.getUnidade().isBlank()) {
            produto.setUnidade("UN");
        }
        if (produto.getPrecoCusto() == null) {
            produto.setPrecoCusto(BigDecimal.ZERO);
        }
    }

    private Produto buscarPorUuid(UUID uuid) {
        return produtoRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
    }
}
