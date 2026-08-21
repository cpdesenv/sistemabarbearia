package com.barbearia.financeiro.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.barbearia.financeiro.domain.Despesa;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    Optional<Despesa> findByUuidPublico(UUID uuidPublico);

    List<Despesa> findByDataBetweenOrderByDataDesc(LocalDate inicio, LocalDate fim);

    List<Despesa> findAllByOrderByDataDesc();

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d")
    BigDecimal somarTodasAsDespesas();
}
