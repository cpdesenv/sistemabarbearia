package com.barbearia.mensageria.gateway;

import java.util.List;

/**
 * Envio de mensagens de WhatsApp. Assinaturas pensadas para a Cloud API
 * caber sem mudanca de contrato quando a Fase 6-META trocar a implementacao
 * (ver {@code MockWhatsAppGateway}, unica implementacao ate la).
 */
public interface WhatsAppGateway {

    /** @return o id da mensagem atribuido pelo provedor ({@code waMessageId}). */
    String sendMessage(String telefoneE164, String texto);

    String sendTemplate(String telefoneE164, String nomeTemplate, List<String> parametros);

    String sendInteractive(String telefoneE164, String corpo, List<String> opcoes);

    String sendDocument(String telefoneE164, byte[] conteudo, String nomeArquivo, String legenda);
}
