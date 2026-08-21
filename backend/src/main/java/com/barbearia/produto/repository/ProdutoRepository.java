package com.barbearia.produto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.produto.domain.Produto;

public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    Optional<Produto> findByUuidPublico(UUID uuidPublico);

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.estoqueAtual <= p.estoqueMinimo "
            + "ORDER BY (p.estoqueAtual - p.estoqueMinimo) ASC")
    List<Produto> buscarAbaixoDoEstoqueMinimo();

    /**
     * Ajuste atomico de saldo: so' aplica o delta se o resultado continuar >= 0,
     * evitando que duas baixas concorrentes (ex.: dois fechamentos de comanda
     * simultaneos vendendo o ultimo item do estoque) deixem o saldo negativo.
     * Retorna 0 linhas afetadas quando nao ha saldo suficiente — quem chama
     * decide o que fazer com isso (ver {@code EstoqueService}).
     *
     * <p>Executa em SQL/JPQL de UPDATE direto, ignorando o first-level cache do
     * Hibernate: qualquer {@link Produto} ja carregado na mesma
     * transacao/EntityManager fica com {@code estoqueAtual} desatualizado em
     * memoria depois desta chamada e precisa ser recarregado (ex.:
     * {@code entityManager.refresh(produto)}) antes de ser devolvido ao
     * chamador.
     */
    @Modifying
    @Query("UPDATE Produto p SET p.estoqueAtual = p.estoqueAtual + :delta, p.atualizadoEm = CURRENT_TIMESTAMP "
            + "WHERE p.id = :id AND p.estoqueAtual + :delta >= 0")
    int ajustarEstoque(@Param("id") Long id, @Param("delta") int delta);
}
