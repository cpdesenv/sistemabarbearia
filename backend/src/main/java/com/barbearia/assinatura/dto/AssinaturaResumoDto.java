package com.barbearia.assinatura.dto;

import java.math.BigDecimal;

public record AssinaturaResumoDto(
        long ativas,
        long inadimplentes,
        long suspensas,
        long canceladas,
        BigDecimal receitaRecorrenteMensal) {
}
