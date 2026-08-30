package com.barbearia.relatorio.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbearia.relatorio.domain.RelatorioHorarioDiario;

public interface RelatorioHorarioDiarioRepository extends JpaRepository<RelatorioHorarioDiario, Long> {

    void deleteByData(LocalDate data);

    List<RelatorioHorarioDiario> findByDataBetween(LocalDate dataInicial, LocalDate dataFinal);
}
