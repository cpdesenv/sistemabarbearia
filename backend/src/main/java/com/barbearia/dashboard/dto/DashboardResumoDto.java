package com.barbearia.dashboard.dto;

public record DashboardResumoDto(
        DashboardCardsDto cards,
        IndicadoresSaudeDto indicadoresSaude,
        IndicadoresAssinaturaDto indicadoresAssinatura,
        DashboardGraficosDto graficos) {
}
