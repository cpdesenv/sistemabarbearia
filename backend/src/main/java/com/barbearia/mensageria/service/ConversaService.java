package com.barbearia.mensageria.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.ia.repository.UsoLlmRepository;
import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.ModoAtendimento;
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
    private final UsoLlmRepository usoLlmRepository;

    @Transactional(readOnly = true)
    public ConversaDto obter(UUID conversaUuid) {
        return paraDto(buscarPorUuid(conversaUuid));
    }

    @Transactional(readOnly = true)
    public Page<ConversaDto> listar(ModoAtendimento status, Pageable pageable) {
        Page<Conversa> pagina = status != null
                ? conversaRepository.findAllByModoAtendimentoOrderByUltimaMensagemEmDesc(status, pageable)
                : conversaRepository.findAllByOrderByUltimaMensagemEmDesc(pageable);
        return pagina.map(this::paraDto);
    }

    /** Botao "assumir conversa" do painel: encerra o atendimento automatico, um humano assume dali em diante. */
    @Transactional
    public ConversaDto assumir(UUID conversaUuid) {
        Conversa conversa = buscarPorUuid(conversaUuid);
        conversa.setModoAtendimento(ModoAtendimento.HUMANO);
        conversa.setMotivoEscalonamento("Assumida manualmente pelo painel.");
        return paraDto(conversaRepository.save(conversa));
    }

    @Transactional(readOnly = true)
    public List<MensagemDto> listarMensagens(UUID conversaUuid) {
        Conversa conversa = buscarPorUuid(conversaUuid);

        return mensagemRepository.findByConversaOrderByCriadoEmAsc(conversa).stream()
                .map(mensagem -> new MensagemDto(mensagem.getUuidPublico(), mensagem.getDirecao(),
                        mensagem.getTipo(), mensagem.getConteudo(), mensagem.getStatus(), mensagem.getCriadoEm()))
                .toList();
    }

    private ConversaDto paraDto(Conversa conversa) {
        return new ConversaDto(conversa.getUuidPublico(), conversa.getCliente().getUuidPublico(),
                conversa.getCliente().getNome(), conversa.getTelefoneE164(), conversa.getUltimaMensagemEm(),
                conversa.getModoAtendimento(), conversa.getMotivoEscalonamento(), conversa.getTurnosIa(),
                usoLlmRepository.somarCustoCentavosDaConversa(conversa));
    }

    private Conversa buscarPorUuid(UUID uuid) {
        return conversaRepository.findByUuidPublico(uuid)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa nao encontrada."));
    }
}
