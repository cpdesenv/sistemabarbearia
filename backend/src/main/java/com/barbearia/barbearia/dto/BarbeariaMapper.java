package com.barbearia.barbearia.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.barbearia.barbearia.domain.Barbearia;

@Mapper(componentModel = "spring")
public interface BarbeariaMapper {

    BarbeariaDto paraDto(Barbearia barbearia);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    void atualizar(AtualizarBarbeariaRequest requisicao, @MappingTarget Barbearia barbearia);
}
