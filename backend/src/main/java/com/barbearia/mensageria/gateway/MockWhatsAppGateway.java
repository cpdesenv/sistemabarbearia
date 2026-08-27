package com.barbearia.mensageria.gateway;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simula o envio sem chamar nenhum provedor real — usada em dev e na suite
 * de testes, sem nenhuma credencial. Padrao quando {@code whatsapp.gateway}
 * nao esta configurado.
 *
 * <p>{@link #simularFalhaNoProximoEnvio()} arma uma falha de uso unico
 * (consumida no proximo {@code send*}), usada pelo simulador/testes para
 * exercitar o outbox de retentativa de forma deterministica.
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "gateway", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppGateway.class);

    private final AtomicBoolean falharProximoEnvio = new AtomicBoolean(false);

    public void simularFalhaNoProximoEnvio() {
        falharProximoEnvio.set(true);
    }

    @Override
    public String sendMessage(String telefoneE164, String texto) {
        return enviar(telefoneE164, "texto", texto);
    }

    @Override
    public String sendTemplate(String telefoneE164, String nomeTemplate, List<String> parametros) {
        return enviar(telefoneE164, "template", nomeTemplate + " " + parametros);
    }

    @Override
    public String sendInteractive(String telefoneE164, String corpo, List<String> opcoes) {
        return enviar(telefoneE164, "interativo", corpo + " " + opcoes);
    }

    @Override
    public String sendDocument(String telefoneE164, byte[] conteudo, String nomeArquivo, String legenda) {
        return enviar(telefoneE164, "documento", nomeArquivo);
    }

    private String enviar(String telefoneE164, String tipo, Object resumo) {
        if (falharProximoEnvio.compareAndSet(true, false)) {
            throw new WhatsAppEnvioException("Falha de envio simulada.");
        }
        String waMessageId = "mock-msg-" + UUID.randomUUID();
        log.info("[MOCK WHATSAPP] Envio ({}) para {} -> waMessageId={}: {}", tipo, telefoneE164, waMessageId, resumo);
        return waMessageId;
    }
}
