package com.barbearia.assinatura.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.agenda.service.AvailabilityService;
import com.barbearia.assinatura.domain.PlanoAssinatura;
import com.barbearia.assinatura.dto.AtualizarStatusPlanoAssinaturaRequest;
import com.barbearia.assinatura.dto.PlanoAssinaturaDto;
import com.barbearia.assinatura.dto.PlanoAssinaturaMapper;
import com.barbearia.assinatura.dto.SalvarPlanoAssinaturaRequest;
import com.barbearia.assinatura.repository.PlanoAssinaturaRepository;
import com.barbearia.servico.domain.Servico;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class PlanoAssinaturaService {

    private final PlanoAssinaturaRepository planoAssinaturaRepository;
    private final PlanoAssinaturaMapper planoAssinaturaMapper;
    private final AvailabilityService availabilityService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<PlanoAssinaturaDto> listar(Boolean ativo) {
        List<PlanoAssinatura> planos = ativo != null
                ? planoAssinaturaRepository.findByAtivoOrderByNome(ativo)
                : planoAssinaturaRepository.findAllByOrderByNome();
        return planos.stream().map(planoAssinaturaMapper::paraDto).toList();
    }

    @Transactional(readOnly = true)
    public PlanoAssinaturaDto obter(UUID uuid) {
        return planoAssinaturaMapper.paraDto(buscarPorUuid(uuid));
    }

    @Transactional
    public PlanoAssinaturaDto criar(SalvarPlanoAssinaturaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        PlanoAssinatura plano = new PlanoAssinatura();
        copiarPara(requisicao, plano);
        plano = planoAssinaturaRepository.save(plano);

        auditoriaService.registrar(usuarioId, "PLANO_ASSINATURA_CRIADO", "plano_assinatura", plano.getId(),
                "Plano '" + plano.getNome() + "' criado", httpRequest);

        return planoAssinaturaMapper.paraDto(plano);
    }

    @Transactional
    public PlanoAssinaturaDto atualizar(UUID uuid, SalvarPlanoAssinaturaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        PlanoAssinatura plano = buscarPorUuid(uuid);
        copiarPara(requisicao, plano);
        plano = planoAssinaturaRepository.save(plano);

        auditoriaService.registrar(usuarioId, "PLANO_ASSINATURA_ATUALIZADO", "plano_assinatura", plano.getId(),
                "Plano '" + plano.getNome() + "' atualizado", httpRequest);

        return planoAssinaturaMapper.paraDto(plano);
    }

    @Transactional
    public PlanoAssinaturaDto atualizarStatus(UUID uuid, AtualizarStatusPlanoAssinaturaRequest requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        PlanoAssinatura plano = buscarPorUuid(uuid);
        plano.setAtivo(requisicao.ativo());
        plano = planoAssinaturaRepository.save(plano);

        String operacao = requisicao.ativo() ? "PLANO_ASSINATURA_ATIVADO" : "PLANO_ASSINATURA_DESATIVADO";
        auditoriaService.registrar(usuarioId, operacao, "plano_assinatura", plano.getId(),
                "Plano '" + plano.getNome() + "' " + (requisicao.ativo() ? "ativado" : "desativado"), httpRequest);

        return planoAssinaturaMapper.paraDto(plano);
    }

    private void copiarPara(SalvarPlanoAssinaturaRequest requisicao, PlanoAssinatura plano) {
        plano.setNome(requisicao.nome());
        plano.setDescricao(requisicao.descricao());
        plano.setPrecoMensal(requisicao.precoMensal());
        plano.setCortesIncluidosPorCiclo(requisicao.cortesIncluidosPorCiclo());
        plano.setPercentualDescontoAdicional(requisicao.percentualDescontoAdicional());

        List<Servico> servicos = availabilityService.resolverServicosAtivos(requisicao.servicosInclusosUuids());
        Set<Servico> servicosInclusos = new HashSet<>(servicos);
        plano.setServicosInclusos(servicosInclusos);
    }

    private PlanoAssinatura buscarPorUuid(UUID uuid) {
        return planoAssinaturaRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano de assinatura nao encontrado."));
    }
}
