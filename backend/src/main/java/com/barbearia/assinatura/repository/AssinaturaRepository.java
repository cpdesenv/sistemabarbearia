package com.barbearia.assinatura.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.assinatura.domain.StatusAssinatura;

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {

    Optional<Assinatura> findByUuidPublico(UUID uuidPublico);

    Optional<Assinatura> findByCliente_IdAndStatus(Long clienteId, StatusAssinatura status);

    List<Assinatura> findByCliente_UuidPublicoOrderByCriadoEmDesc(UUID clienteUuidPublico);

    List<Assinatura> findByStatusOrderByCriadoEmDesc(StatusAssinatura status);

    List<Assinatura> findAllByOrderByCriadoEmDesc();

    long countByStatus(StatusAssinatura status);

    long countByStatusAndDataCancelamentoBetween(StatusAssinatura status, LocalDate inicio, LocalDate fim);

    List<Assinatura> findByStatusInAndDataProximaRenovacaoLessThanEqual(List<StatusAssinatura> status,
            LocalDate data);

    @Query("SELECT COALESCE(SUM(a.plano.precoMensal), 0) FROM Assinatura a WHERE a.status = :status")
    BigDecimal somarPrecoMensalPorStatus(@Param("status") StatusAssinatura status);

    /**
     * Ajuste atomico de saldo de cortes, no mesmo padrao de
     * {@code ProdutoRepository#ajustarEstoque}: so' aplica o delta se o
     * resultado continuar >= 0, evitando que dois fechamentos de comanda
     * simultaneos do mesmo cliente consumam saldo em duplicidade. Retorna 0
     * linhas afetadas quando nao ha saldo suficiente.
     */
    @Modifying
    @Query("UPDATE Assinatura a SET a.saldoCortesAtual = a.saldoCortesAtual + :delta, "
            + "a.atualizadoEm = CURRENT_TIMESTAMP WHERE a.id = :id AND a.saldoCortesAtual + :delta >= 0")
    int ajustarSaldo(@Param("id") Long id, @Param("delta") int delta);
}
