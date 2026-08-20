package com.barbearia.barbearia.dto;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.barbearia.barbearia.domain.Barbearia;

@Mapper(componentModel = "spring")
public interface BarbeariaMapper {

    BarbeariaDto paraDto(Barbearia barbearia);

    void atualizar(AtualizarBarbeariaRequest requisicao, @MappingTarget Barbearia barbearia);
}
