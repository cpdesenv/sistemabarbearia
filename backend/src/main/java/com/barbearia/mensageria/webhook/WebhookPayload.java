package com.barbearia.mensageria.webhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Formato de payload da Cloud API do WhatsApp (mesmo sem provedor real —
 * ver PRD Fase 9), simplificado ao minimo necessario para extrair a
 * mensagem de texto recebida. Usado tanto pelo webhook real
 * ({@code /api/webhook/whatsapp}) quanto pelo simulador
 * ({@code /api/dev/whatsapp/inbound}), que injeta o mesmo formato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(List<Entry> entry) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(List<Change> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(Value value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(List<MensagemPayload> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MensagemPayload(String id, String from, String type, TextoPayload text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextoPayload(String body) {
    }

    /** Extrai as mensagens de texto do payload, ignorando entradas incompletas/de outros tipos. */
    public List<MensagemPayload> extrairMensagensDeTexto() {
        if (entry == null) {
            return List.of();
        }
        return entry.stream()
                .filter(e -> e.changes() != null)
                .flatMap(e -> e.changes().stream())
                .filter(c -> c.value() != null && c.value().messages() != null)
                .flatMap(c -> c.value().messages().stream())
                .filter(m -> "text".equals(m.type()) && m.text() != null && m.id() != null && m.from() != null)
                .toList();
    }
}
