package com.barbearia.profissional.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.barbearia.profissional.domain.Profissional;

@Mapper(componentModel = "spring")
public interface ProfissionalMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    ProfissionalDto paraDto(Profissional profissional);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuidPublico", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    void copiarPara(SalvarProfissionalRequest requisicao, @MappingTarget Profissional profissional);
}
