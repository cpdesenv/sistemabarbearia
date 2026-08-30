package com.barbearia.relatorio.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.relatorio.domain.RelatorioProdutoDiario;

public interface RelatorioProdutoDiarioRepository extends JpaRepository<RelatorioProdutoDiario, Long> {

    void deleteByData(LocalDate data);

    List<RelatorioProdutoDiario> findByDataBetween(LocalDate dataInicial, LocalDate dataFinal);
}
