package com.barbearia.mensageria.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.barbearia.mensageria.domain.Mensagem;
import com.barbearia.mensageria.domain.MensagemEnvioOutbox;
import com.barbearia.mensageria.domain.StatusEnvioOutbox;
import com.barbearia.mensageria.domain.StatusMensagem;
import com.barbearia.mensageria.gateway.WhatsAppGateway;
import com.barbearia.mensageria.repository.MensagemEnvioOutboxRepository;
import com.barbearia.mensageria.repository.MensagemRepository;

/**
 * Processa a fila de envio de mensagem (padrao outbox, mesmo estilo do
 * {@code CalendarOutboxWorker} da Fase 8): uma falha de envio (simulada
 * pelo {@code MockWhatsAppGateway}) nunca perde a mensagem, so atrasa,
 * com retentativa e backoff exponencial.
 */
@Component
public class MensagemEnvioOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(MensagemEnvioOutboxWorker.class);

    private static final int MAX_TENTATIVAS = 8;
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30)
    };

    private final MensagemEnvioOutboxRepository outboxRepository;
    private final MensagemRepository mensagemRepository;
    private final WhatsAppGateway whatsAppGateway;

    /**
     * Referencia ao proprio bean (proxy), injetada de forma preguicosa —
     * necessaria porque chamar processarUm(id) diretamente (this.processarUm)
     * de dentro de processarPendencias() NAO passa pelo proxy do Spring, e
     * por isso @Transactional seria ignorado nessa chamada interna (o
     * classico "self-invocation problem" documentado pelo proprio Spring).
     */
    private final MensagemEnvioOutboxWorker self;

    public MensagemEnvioOutboxWorker(MensagemEnvioOutboxRepository outboxRepository,
            MensagemRepository mensagemRepository, WhatsAppGateway whatsAppGateway,
            @Lazy MensagemEnvioOutboxWorker self) {
        this.outboxRepository = outboxRepository;
        this.mensagemRepository = mensagemRepository;
        this.whatsAppGateway = whatsAppGateway;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${whatsapp.outbox-intervalo-ms:5000}")
    public void processarPendencias() {
        List<Long> idsPendentes = outboxRepository
                .findByStatusAndProximaTentativaEmLessThanEqualOrderByProximaTentativaEmAsc(
                        StatusEnvioOutbox.PENDENTE, Instant.now())
                .stream()
                .map(MensagemEnvioOutbox::getId)
                .toList();

        for (Long id : idsPendentes) {
            self.processarUm(id);
        }
    }

    @Transactional
    public void processarUm(Long outboxId) {
        MensagemEnvioOutbox linha = outboxRepository.findById(outboxId).orElse(null);
        if (linha == null || linha.getStatus() != StatusEnvioOutbox.PENDENTE) {
            return;
        }

        Mensagem mensagem = linha.getMensagem();
        try {
            String waMessageId = whatsAppGateway.sendMessage(mensagem.getConversa().getTelefoneE164(),
                    mensagem.getConteudo());
            mensagem.setWaMessageId(waMessageId);
            mensagem.setStatus(StatusMensagem.ENVIADA);
            mensagemRepository.save(mensagem);

            linha.setStatus(StatusEnvioOutbox.CONCLUIDO);
            linha.setUltimoErro(null);
            outboxRepository.save(linha);
        } catch (Exception e) {
            registrarFalha(linha, mensagem, e);
        }
    }

    private void registrarFalha(MensagemEnvioOutbox linha, Mensagem mensagem, Exception e) {
        int tentativas = linha.getTentativas() + 1;
        String mensagemErro = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        linha.setTentativas(tentativas);
        linha.setUltimoErro(mensagemErro);
        if (tentativas >= MAX_TENTATIVAS) {
            linha.setStatus(StatusEnvioOutbox.FALHA_PERMANENTE);
            mensagem.setStatus(StatusMensagem.FALHA);
            mensagemRepository.save(mensagem);
        } else {
            linha.setProximaTentativaEm(Instant.now().plus(calcularBackoff(tentativas)));
        }
        outboxRepository.save(linha);

        log.warn("Falha ao enviar mensagem {} (tentativa {}/{}): {}", mensagem.getUuidPublico(), tentativas,
                MAX_TENTATIVAS, mensagemErro);
    }

    private Duration calcularBackoff(int tentativas) {
        int indice = Math.min(tentativas - 1, BACKOFF.length - 1);
        return BACKOFF[indice];
    }
}
