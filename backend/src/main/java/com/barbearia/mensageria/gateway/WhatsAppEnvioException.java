package com.barbearia.mensageria.gateway;

/**
 * Falha ao enviar mensagem pelo WhatsApp. Capturada pelo
 * {@code MensagemEnvioOutboxWorker}, nunca pelo fluxo que cria a mensagem —
 * e por isso que existe o outbox.
 */
public class WhatsAppEnvioException extends RuntimeException {

    public WhatsAppEnvioException(String mensagem) {
        super(mensagem);
    }
}
