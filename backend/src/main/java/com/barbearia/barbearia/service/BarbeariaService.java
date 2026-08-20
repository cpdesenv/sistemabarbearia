package com.barbearia.barbearia.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.barbearia.domain.Barbearia;
import com.barbearia.barbearia.dto.AtualizarBarbeariaRequest;
import com.barbearia.barbearia.dto.BarbeariaDto;
import com.barbearia.barbearia.dto.BarbeariaMapper;
import com.barbearia.barbearia.repository.BarbeariaRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class BarbeariaService {

    private final BarbeariaRepository barbeariaRepository;
    private final BarbeariaMapper barbeariaMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public BarbeariaDto obter() {
        return barbeariaMapper.paraDto(buscarSingleton());
    }

    @Transactional
    public BarbeariaDto atualizar(AtualizarBarbeariaRequest requisicao, Long usuarioId,
            HttpServletRequest httpRequest) {
        Barbearia barbearia = buscarSingleton();
        barbeariaMapper.atualizar(requisicao, barbearia);
        barbearia = barbeariaRepository.save(barbearia);

        auditoriaService.registrar(usuarioId, "BARBEARIA_ATUALIZADA", "barbearia", Barbearia.ID_SINGLETON,
                "Configuracao da barbearia atualizada", httpRequest);

        return barbeariaMapper.paraDto(barbearia);
    }

    private Barbearia buscarSingleton() {
        return barbeariaRepository.findById(Barbearia.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao da barbearia nao encontrada. Verifique se as migrations foram executadas."));
    }
}
