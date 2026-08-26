package com.barbearia.financeiro.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import com.barbearia.assinatura.domain.Assinatura;
import com.barbearia.financeiro.domain.ContaReceber;
import com.barbearia.financeiro.domain.StatusContaReceber;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long> {

    Optional<ContaReceber> findByUuidPublico(UUID uuidPublico);

    List<ContaReceber> findByStatusOrderByDataVencimento(StatusContaReceber status);

    List<ContaReceber> findByCliente_UuidPublicoOrderByDataVencimento(UUID clienteUuidPublico);

    List<ContaReceber> findAllByOrderByDataVencimento();

    Optional<ContaReceber> findTopByAssinaturaOrderByDataVencimentoDesc(Assinatura assinatura);

    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM ContaReceber c WHERE c.status = :status")
    BigDecimal somarPorStatus(@Param("status") StatusContaReceber status);

    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM ContaReceber c WHERE c.assinatura IS NOT NULL "
            + "AND c.status = :status AND c.dataRecebimento BETWEEN :inicio AND :fim")
    BigDecimal somarMensalidadesRecebidasNoPeriodo(@Param("status") StatusContaReceber status,
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
