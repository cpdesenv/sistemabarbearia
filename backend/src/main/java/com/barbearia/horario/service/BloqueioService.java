package com.barbearia.horario.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.horario.domain.Bloqueio;
import com.barbearia.horario.dto.BloqueioDto;
import com.barbearia.horario.dto.BloqueioMapper;
import com.barbearia.horario.dto.CriarBloqueioRequest;
import com.barbearia.horario.repository.BloqueioRepository;
import com.barbearia.horario.repository.BloqueioSpecifications;
import com.barbearia.profissional.domain.Profissional;
import com.barbearia.profissional.repository.ProfissionalRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.NegocioException;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class BloqueioService {

    private final BloqueioRepository bloqueioRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BloqueioMapper bloqueioMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<BloqueioDto> listar(UUID profissionalUuid, Instant de, Instant ate, Pageable pageable) {
        return bloqueioRepository.findAll(BloqueioSpecifications.comFiltros(profissionalUuid, de, ate), pageable)
                .map(bloqueioMapper::paraDto);
    }

    @Transactional
    public BloqueioDto criar(CriarBloqueioRequest requisicao, Long usuarioId, HttpServletRequest httpRequest) {
        if (!requisicao.fim().isAfter(requisicao.inicio())) {
            throw new NegocioException("O fim do bloqueio deve ser depois do inicio.");
        }

        Profissional profissional = null;
        if (requisicao.profissionalUuid() != null) {
            profissional = profissionalRepository.findByUuidPublico(requisicao.profissionalUuid())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional nao encontrado."));
        }

        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setProfissional(profissional);
        bloqueio.setInicio(requisicao.inicio());
        bloqueio.setFim(requisicao.fim());
        bloqueio.setMotivo(requisicao.motivo());
        bloqueio = bloqueioRepository.save(bloqueio);

        auditoriaService.registrar(usuarioId, "BLOQUEIO_CRIADO", "bloqueio", bloqueio.getId(),
                "Bloqueio '" + bloqueio.getMotivo() + "' criado"
                        + (profissional != null ? " para " + profissional.getNome() : " (global)"),
                httpRequest);

        return bloqueioMapper.paraDto(bloqueio);
    }

    @Transactional
    public void remover(UUID uuid, Long usuarioId, HttpServletRequest httpRequest) {
        Bloqueio bloqueio = bloqueioRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio nao encontrado."));

        auditoriaService.registrar(usuarioId, "BLOQUEIO_REMOVIDO", "bloqueio", bloqueio.getId(),
                "Bloqueio '" + bloqueio.getMotivo() + "' removido", httpRequest);

        bloqueioRepository.delete(bloqueio);
    }
}
