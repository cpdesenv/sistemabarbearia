package com.barbearia.cliente.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.barbearia.cliente.domain.Cliente;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    @Mapping(target = "anonimizado", expression = "java(cliente.isAnonimizado())")
    ClienteDto paraDto(Cliente cliente);

    // Telefone e CPF sao normalizados/validados no service antes de serem
    // atribuidos, e consentimento/origem/timestamps tem regras proprias — por
    // isso ficam de fora da copia automatica.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuidPublico", ignore = true)
    @Mapping(target = "telefone", ignore = true)
    @Mapping(target = "cpf", ignore = true)
    @Mapping(target = "origemCadastro", ignore = true)
    @Mapping(target = "consentimentoLgpd", ignore = true)
    @Mapping(target = "consentimentoLgpdEm", ignore = true)
    @Mapping(target = "anonimizadoEm", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    void copiarPara(SalvarClienteRequest requisicao, @MappingTarget Cliente cliente);
}
