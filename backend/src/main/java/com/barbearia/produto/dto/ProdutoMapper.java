package com.barbearia.produto.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.barbearia.produto.domain.Produto;

@Mapper(componentModel = "spring")
public interface ProdutoMapper {

    @Mapping(source = "uuidPublico", target = "uuid")
    ProdutoDto paraDto(Produto produto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuidPublico", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "estoqueAtual", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    void copiarPara(SalvarProdutoRequest requisicao, @MappingTarget Produto produto);
}
