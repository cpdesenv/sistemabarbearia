package com.barbearia.servico.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.barbearia.servico.domain.Servico;

@Mapper(componentModel = "spring")
public interface ServicoMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    ServicoDto paraDto(Servico servico);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuidPublico", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    void copiarPara(SalvarServicoRequest requisicao, @MappingTarget Servico servico);
}
