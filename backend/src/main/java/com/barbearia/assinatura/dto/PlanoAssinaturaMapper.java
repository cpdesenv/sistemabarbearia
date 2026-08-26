package com.barbearia.assinatura.dto;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;

import com.barbearia.assinatura.domain.PlanoAssinatura;
import com.barbearia.servico.domain.Servico;

@Mapper(componentModel = "spring")
public interface PlanoAssinaturaMapper {

    default PlanoAssinaturaDto paraDto(PlanoAssinatura plano) {
        List<UUID> servicosUuids = plano.getServicosInclusos().stream().map(Servico::getUuidPublico).toList();
        return new PlanoAssinaturaDto(plano.getUuidPublico(), plano.getNome(), plano.getDescricao(),
                plano.getPrecoMensal(), plano.getCortesIncluidosPorCiclo(), plano.getPercentualDescontoAdicional(),
                plano.isAtivo(), servicosUuids, plano.getCriadoEm(), plano.getAtualizadoEm());
    }
}
