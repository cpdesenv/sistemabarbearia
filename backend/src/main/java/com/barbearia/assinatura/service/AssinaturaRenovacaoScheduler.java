package com.barbearia.assinatura.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Dispara diariamente a renovacao de assinaturas — ver {@link AssinaturaService#processarRenovacoes()}. */
@Component
@RequiredArgsConstructor
public class AssinaturaRenovacaoScheduler {

    private final AssinaturaService assinaturaService;

    @Scheduled(cron = "0 0 3 * * *")
    public void executar() {
        assinaturaService.processarRenovacoes();
    }
}
