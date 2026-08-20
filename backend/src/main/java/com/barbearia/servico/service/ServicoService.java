package com.barbearia.servico.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.servico.domain.Servico;
import com.barbearia.servico.dto.AtualizarStatusServicoRequest;
import com.barbearia.servico.dto.SalvarServicoRequest;
import com.barbearia.servico.dto.ServicoDto;
import com.barbearia.servico.dto.ServicoMapper;
import com.barbearia.servico.repository.ServicoRepository;
import com.barbearia.servico.repository.ServicoSpecifications;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final ServicoMapper servicoMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<ServicoDto> listar(String nome, String categoria, Boolean ativo, Pageable pageable) {
        return servicoRepository.findAll(ServicoSpecifications.comFiltros(nome, categoria, ativo), pageable)
                .map(servicoMapper::paraDto);
    }

    @Transactional(readOnly = true)
    public ServicoDto obter(UUID uuid) {
        return servicoMapper.paraDto(buscarPorUuid(uuid));
    }

    @Transactional
    public ServicoDto criar(SalvarServicoRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        Servico servico = new Servico();
        servicoMapper.copiarPara(requisicao, servico);
        servico = servicoRepository.save(servico);

        auditoriaService.registrar(usuarioId, "SERVICO_CRIADO", "servico", servico.getId(),
                "Servico '" + servico.getNome() + "' criado", httpRequest);

        return servicoMapper.paraDto(servico);
    }

    @Transactional
    public ServicoDto atualizar(UUID uuid, SalvarServicoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Servico servico = buscarPorUuid(uuid);
        servicoMapper.copiarPara(requisicao, servico);
        servico = servicoRepository.save(servico);

        auditoriaService.registrar(usuarioId, "SERVICO_ATUALIZADO", "servico", servico.getId(),
                "Servico '" + servico.getNome() + "' atualizado", httpRequest);

        return servicoMapper.paraDto(servico);
    }

    @Transactional
    public ServicoDto atualizarStatus(UUID uuid, AtualizarStatusServicoRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Servico servico = buscarPorUuid(uuid);
        servico.setAtivo(requisicao.ativo());
        servico = servicoRepository.save(servico);

        String operacao = requisicao.ativo() ? "SERVICO_ATIVADO" : "SERVICO_DESATIVADO";
        auditoriaService.registrar(usuarioId, operacao, "servico", servico.getId(),
                "Servico '" + servico.getNome() + "' " + (requisicao.ativo() ? "ativado" : "desativado"),
                httpRequest);

        return servicoMapper.paraDto(servico);
    }

    private Servico buscarPorUuid(UUID uuid) {
        return servicoRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico nao encontrado."));
    }
}
