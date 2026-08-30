package com.barbearia.relatorio.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.barbearia.relatorio.domain.RelatorioServicoDiario;

public interface RelatorioServicoDiarioRepository extends JpaRepository<RelatorioServicoDiario, Long>,
        JpaSpecificationExecutor<RelatorioServicoDiario> {

    void deleteByData(LocalDate data);

    List<RelatorioServicoDiario> findByData(LocalDate data);
}
