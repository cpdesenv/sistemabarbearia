package com.barbearia.fiscal.dto;

import java.time.Instant;
import java.util.UUID;

import com.barbearia.fiscal.domain.StatusComprovante;

public record ComprovanteDto(UUID uuid, long numero, StatusComprovante status, Instant geradoEm) {
}
