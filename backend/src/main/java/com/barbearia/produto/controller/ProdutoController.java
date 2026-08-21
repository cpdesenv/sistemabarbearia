package com.barbearia.produto.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.produto.dto.AjusteEstoqueRequest;
import com.barbearia.produto.dto.AtualizarStatusProdutoRequest;
import com.barbearia.produto.dto.EntradaEstoqueRequest;
import com.barbearia.produto.dto.MovimentoEstoqueDto;
import com.barbearia.produto.dto.ProdutoDto;
import com.barbearia.produto.dto.SalvarProdutoRequest;
import com.barbearia.produto.service.EstoqueService;
import com.barbearia.produto.service.ProdutoService;
import com.barbearia.shared.security.UsuarioAutenticado;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Tag(name = "Produtos")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;

    @GetMapping
    public PagedModel<ProdutoDto> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        Page<ProdutoDto> pagina = produtoService.listar(nome, categoria, ativo, pageable);
        return new PagedModel<>(pagina);
    }

    @GetMapping("/alertas-estoque-minimo")
    public List<ProdutoDto> alertasEstoqueMinimo() {
        return produtoService.alertaEstoqueMinimo();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ProdutoDto> obter(@PathVariable UUID uuid) {
        return ResponseEntity.ok(produtoService.obter(uuid));
    }

    @GetMapping("/{uuid}/movimentos")
    public PagedModel<MovimentoEstoqueDto> movimentos(@PathVariable UUID uuid, Pageable pageable) {
        return new PagedModel<>(estoqueService.extrato(uuid, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ProdutoDto criar(@Valid @RequestBody SalvarProdutoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return produtoService.criar(requisicao, principal.getUsuario().getId(), httpRequest);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProdutoDto> atualizar(@PathVariable UUID uuid,
            @Valid @RequestBody SalvarProdutoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(produtoService.atualizar(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PatchMapping("/{uuid}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProdutoDto> atualizarStatus(@PathVariable UUID uuid,
            @Valid @RequestBody AtualizarStatusProdutoRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(produtoService.atualizarStatus(uuid, requisicao, principal.getUsuario().getId(),
                httpRequest));
    }

    @PostMapping("/{uuid}/entrada-estoque")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProdutoDto> entradaEstoque(@PathVariable UUID uuid,
            @Valid @RequestBody EntradaEstoqueRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(estoqueService.registrarEntrada(uuid, requisicao.quantidade(),
                requisicao.custoUnitario(), requisicao.motivo(), principal.getUsuario().getId(), httpRequest));
    }

    @PostMapping("/{uuid}/ajuste-estoque")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<ProdutoDto> ajusteEstoque(@PathVariable UUID uuid,
            @Valid @RequestBody AjusteEstoqueRequest requisicao,
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(estoqueService.registrarAjuste(uuid, requisicao.novaQuantidadeContada(),
                requisicao.motivo(), principal.getUsuario().getId(), httpRequest));
    }
}
