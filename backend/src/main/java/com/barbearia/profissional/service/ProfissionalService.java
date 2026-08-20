package com.barbearia.profissional.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.domain.ProfissionalServico;
import com.barbearia.profissional.dto.AssociarServicoRequest;
import com.barbearia.profissional.dto.AtualizarStatusProfissionalRequest;
import com.barbearia.profissional.dto.ProfissionalDto;
import com.barbearia.profissional.dto.ProfissionalMapper;
import com.barbearia.profissional.dto.SalvarProfissionalRequest;
import com.barbearia.profissional.dto.ServicoVinculadoDto;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.profissional.repository.ProfissionalServicoRepository;
import com.barbearia.profissional.repository.ProfissionalSpecifications;
import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalServicoRepository profissionalServicoRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalMapper profissionalMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<ProfissionalDto> listar(String nome, Boolean ativo, Pageable pageable) {
        return profissionalRepository.findAll(ProfissionalSpecifications.comFiltros(nome, ativo), pageable)
                .map(profissionalMapper::paraDto);
    }

    @Transactional(readOnly = true)
    public ProfissionalDto obter(UUID uuid) {
        return profissionalMapper.paraDto(buscarPorUuid(uuid));
    }

    @Transactional
    public ProfissionalDto criar(SalvarProfissionalRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Profissional profissional = new Profissional();
        profissionalMapper.copiarPara(requisicao, profissional);
        profissional = profissionalRepository.save(profissional);

        auditoriaService.registrar(usuarioId, "PROFISSIONAL_CRIADO", "profissional", profissional.getId(),
                "Profissional '" + profissional.getNome() + "' criado", httpRequest);

        return profissionalMapper.paraDto(profissional);
    }

    @Transactional
    public ProfissionalDto atualizar(UUID uuid, SalvarProfissionalRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Profissional profissional = buscarPorUuid(uuid);
        profissionalMapper.copiarPara(requisicao, profissional);
        profissional = profissionalRepository.save(profissional);

        auditoriaService.registrar(usuarioId, "PROFISSIONAL_ATUALIZADO", "profissional", profissional.getId(),
                "Profissional '" + profissional.getNome() + "' atualizado", httpRequest);

        return profissionalMapper.paraDto(profissional);
    }

    @Transactional
    public ProfissionalDto atualizarStatus(UUID uuid, AtualizarStatusProfissionalRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Profissional profissional = buscarPorUuid(uuid);
        profissional.setAtivo(requisicao.ativo());
        profissional = profissionalRepository.save(profissional);

        String operacao = requisicao.ativo() ? "PROFISSIONAL_ATIVADO" : "PROFISSIONAL_DESATIVADO";
        auditoriaService.registrar(usuarioId, operacao, "profissional", profissional.getId(),
                "Profissional '" + profissional.getNome() + "' " + (requisicao.ativo() ? "ativado" : "desativado"),
                httpRequest);

        return profissionalMapper.paraDto(profissional);
    }

    @Transactional(readOnly = true)
    public List<ServicoVinculadoDto> listarServicosVinculados(UUID uuid) {
        Profissional profissional = buscarPorUuid(uuid);
        return profissionalServicoRepository.findByProfissional(profissional).stream()
                .map(vinculo -> paraServicoVinculadoDto(vinculo, profissional))
                .toList();
    }

    @Transactional
    public List<ServicoVinculadoDto> sincronizarServicos(UUID uuid, List<AssociarServicoRequest> requisicao,
            Long usuarioId, HttpServletRequest httpRequest) {
        Profissional profissional = buscarPorUuid(uuid);

        long uuidsDistintos = requisicao.stream().map(AssociarServicoRequest::servicoUuid).distinct().count();
        if (uuidsDistintos != requisicao.size()) {
            throw new NegocioException("A lista de servicos nao pode conter o mesmo servico duas vezes.");
        }

        profissionalServicoRepository.deleteByProfissional(profissional);

        List<ProfissionalServico> vinculos = requisicao.stream()
                .map(item -> {
                    Servico servico = servicoRepository.findByUuidPublico(item.servicoUuid())
                            .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado."));
                    return new ProfissionalServico(profissional, servico, item.comissaoPercentual());
                })
                .toList();
        profissionalServicoRepository.saveAll(vinculos);

        auditoriaService.registrar(usuarioId, "PROFISSIONAL_SERVICOS_ATUALIZADOS", "profissional", profissional.getId(),
                "Vinculos de servico do profissional '" + profissional.getNome() + "' atualizados ("
                        + vinculos.size() + " servico(s))",
                httpRequest);

        return vinculos.stream().map(vinculo -> paraServicoVinculadoDto(vinculo, profissional)).toList();
    }

    private ServicoVinculadoDto paraServicoVinculadoDto(ProfissionalServico vinculo, Profissional profissional) {
        BigDecimal comissaoEfetiva = vinculo.getComissaoPercentual() != null
                ? vinculo.getComissaoPercentual()
                : profissional.getComissaoPercentualPadrao();
        return new ServicoVinculadoDto(vinculo.getServico().getUuidPublico(), vinculo.getServico().getNome(),
                vinculo.getComissaoPercentual(), comissaoEfetiva);
    }

    private Profissional buscarPorUuid(UUID uuid) {
        return profissionalRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
    }
}
