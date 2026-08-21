package com.barbearia.produto.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.produto.domain.MovimentoEstoque;
import com.barbearia.produto.domain.Produto;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    Page<MovimentoEstoque> findByProdutoOrderByCriadoEmDesc(Produto produto, Pageable pageable);
}
