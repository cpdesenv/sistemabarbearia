package com.barbearia.relatorio.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.relatorio.domain.RelatorioClienteDiario;

public interface RelatorioClienteDiarioRepository extends JpaRepository<RelatorioClienteDiario, Long> {

    void deleteByData(LocalDate data);

    List<RelatorioClienteDiario> findByDataBetween(LocalDate dataInicial, LocalDate dataFinal);
}
