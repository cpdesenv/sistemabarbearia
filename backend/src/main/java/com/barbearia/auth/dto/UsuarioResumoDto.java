package com.barbearia.auth.dto;

import java.util.UUID;

import com.barbearia.usuario.domain.Perfil;

public record UsuarioResumoDto(UUID uuid, String nome, String email, Perfil perfil) {
}
