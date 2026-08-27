package com.barbearia.mensageria.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.domain.Conversa;
import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.MensagemEnvioOutbox;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.domain.TipoMensagem;
import com.barbearia.mensageria.repository.MensagemEnvioOutboxRepository;
import com.barbearia.mensageria.repository.MensagemRepository;

/**
 * Cria a mensagem SAIDA e a enfileira no outbox de envio, na mesma
 * transacao — o {@code MensagemEnvioOutboxWorker} e quem de fato chama o
 * {@code WhatsAppGateway} depois.
 */
@Service
@RequiredArgsConstructor
public class MensageriaEnvioService {

    private final MensagemRepository mensagemRepository;
    private final MensagemEnvioOutboxRepository outboxRepository;

    @Transactional
    public Mensagem enfileirarEnvio(Conversa conversa, String texto) {
        Mensagem saida = new Mensagem();
        saida.setConversa(conversa);
        saida.setDirecao(DirecaoMensagem.SAIDA);
        saida.setTipo(TipoMensagem.TEXTO);
        saida.setConteudo(texto);
        saida.setStatus(StatusMensagem.PENDENTE);
        saida = mensagemRepository.save(saida);

        MensagemEnvioOutbox outbox = new MensagemEnvioOutbox();
        outbox.setMensagem(saida);
        outboxRepository.save(outbox);

        return saida;
    }
}
