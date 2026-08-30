package com.barbearia.relatorio.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

/**
 * Agrega o dia anterior toda madrugada — depois da renovacao de assinaturas
 * (03:00, ver {@code AssinaturaRenovacaoScheduler}) para nao competir pela
 * mesma janela. So' o dia de ontem: o dia corrente entra por consulta ao
 * vivo em {@code RelatorioFaturamentoService}, e correcoes retroativas
 * (ex.: estorno tardio) usam o endpoint de reprocessamento, nao este job.
 */
@Component
@RequiredArgsConstructor
public class RelatorioAgregacaoScheduler {

    private final RelatorioAgregacaoService relatorioAgregacaoService;
    private final BarbeariaRepository barbeariaRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void executar() {
        Barbearia barbearia = barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
        LocalDate ontem = LocalDate.now(ZoneId.of(barbearia.getFusoHorario())).minusDays(1);
        relatorioAgregacaoService.agregarDia(ontem);
    }
}
