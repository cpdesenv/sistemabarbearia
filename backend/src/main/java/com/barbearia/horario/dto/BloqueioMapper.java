package com.barbearia.horario.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.barbearia.horario.domain.Bloqueio;

@Mapper(componentModel = "spring")
public interface BloqueioMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    @Mapping(source = "profissional.uuidPublico", target = "profissionalUuid")
    @Mapping(source = "profissional.nome", target = "profissionalNome")
    BloqueioDto paraDto(Bloqueio bloqueio);
}
