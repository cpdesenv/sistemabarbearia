package com.barbearia.auth.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.barbearia.usuario.domain.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioResumoMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    UsuarioResumoDto paraDto(Usuario usuario);
}
