package com.barbearia.mensageria.service;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.config.MensageriaProperties;
import com.barbearia.mensageria.domain.DirecaoMensagem;
import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.repository.MensagemRepository;

/**
 * Avanca mensagens SAIDA de ENVIADA -> ENTREGUE -> LIDA apos um delay
 * configuravel — simula os recibos de entrega/leitura que um provedor real
 * enviaria por webhook. So existe com o gateway mock (a Fase 6-META troca
 * isso por recibos reais vindos do proprio webhook).
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "gateway", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
public class StatusMensagemSimuladorWorker {

    private final MensagemRepository mensagemRepository;
    private final MensageriaProperties propriedades;

    @Scheduled(fixedDelayString = "${whatsapp.simulacao-status-delay-ms:10000}")
    @Transactional
    public void avancarStatus() {
        Instant limite = Instant.now().minusMillis(propriedades.getSimulacaoStatusDelayMs());
        avancar(StatusMensagem.ENVIADA, StatusMensagem.ENTREGUE, limite);
        avancar(StatusMensagem.ENTREGUE, StatusMensagem.LIDA, limite);
    }

    private void avancar(StatusMensagem de, StatusMensagem para, Instant limite) {
        List<Mensagem> mensagens = mensagemRepository.findByDirecaoAndStatusAndAtualizadoEmLessThanEqual(
                DirecaoMensagem.SAIDA, de, limite);
        for (Mensagem mensagem : mensagens) {
            mensagem.setStatus(para);
        }
        mensagemRepository.saveAll(mensagens);
    }
}
