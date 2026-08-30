package com.barbearia.relatorio.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.relatorio.domain.RelatorioAgendaDiario;

public interface RelatorioAgendaDiarioRepository extends JpaRepository<RelatorioAgendaDiario, Long>,
        JpaSpecificationExecutor<RelatorioAgendaDiario> {

    void deleteByData(LocalDate data);
}
