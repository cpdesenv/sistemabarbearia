package com.barbearia.mensageria.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.dto.ConversaDto;
import com.barbearia.mensageria.dto.MensagemDto;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.mensageria.repository.MensagemRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ConversaService {

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;

    @Transactional(readOnly = true)
    public Page<ConversaDto> listar(Pageable pageable) {
        return conversaRepository.findAllByOrderByUltimaMensagemEmDesc(pageable).map(this::paraDto);
    }

    @Transactional(readOnly = true)
    public List<MensagemDto> listarMensagens(UUID conversaUuid) {
        Conversa conversa = conversaRepository.findByUuidPublico(conversaUuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa nao encontrada."));

        return mensagemRepository.findByConversaOrderByCriadoEmAsc(conversa).stream()
                .map(mensagem -> new MensagemDto(mensagem.getUuidPublico(), mensagem.getDirecao(),
                        mensagem.getTipo(), mensagem.getConteudo(), mensagem.getStatus(), mensagem.getCriadoEm()))
                .toList();
    }

    private ConversaDto paraDto(Conversa conversa) {
        return new ConversaDto(conversa.getUuidPublico(), conversa.getCliente().getUuidPublico(),
                conversa.getCliente().getNome(), conversa.getTelefoneE164(), conversa.getUltimaMensagemEm());
    }
}
