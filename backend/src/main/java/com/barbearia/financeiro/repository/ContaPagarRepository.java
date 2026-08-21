package com.barbearia.financeiro.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barbearia.financeiro.domain.ContaPagar;
import com.barbearia.financeiro.domain.StatusContaPagar;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long> {

    Optional<ContaPagar> findByUuidPublico(UUID uuidPublico);

    List<ContaPagar> findByStatusOrderByDataVencimento(StatusContaPagar status);

    List<ContaPagar> findAllByOrderByDataVencimento();

    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM ContaPagar c "
            + "WHERE c.status = :status AND c.dataVencimento < :hoje")
    BigDecimal somarPorStatusVencidasAte(@Param("status") StatusContaPagar status, @Param("hoje") LocalDate hoje);
}
