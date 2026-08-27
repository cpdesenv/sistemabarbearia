package com.barbearia.mensageria.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.config.MensageriaProperties;
import com.barbearia.mensageria.service.MensageriaInboundService;

/**
 * Webhook no formato da Cloud API do WhatsApp — rota publica (ver
 * SecurityConfig), autorizada pela assinatura HMAC do corpo, nao por JWT
 * (e o proprio provedor quem chama, sem sessao de usuario).
 */
@RestController
@RequestMapping("/api/webhook/whatsapp")
@RequiredArgsConstructor
@Tag(name = "Webhook WhatsApp")
public class WhatsAppWebhookController {

    private final MensageriaProperties propriedades;
    private final MensageriaInboundService inboundService;
    private final ObjectMapper objectMapper;

    /** Verificacao do webhook, no formato exigido pela Cloud API. */
    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode") String modo,
            @RequestParam(name = "hub.verify_token") String tokenVerificacao,
            @RequestParam(name = "hub.challenge") String desafio) {
        if ("subscribe".equals(modo) && propriedades.getWebhookSecret().equals(tokenVerificacao)) {
            return ResponseEntity.ok(desafio);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody String corpoBruto,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura) {
        if (!AssinaturaWebhookValidador.valida(corpoBruto, assinatura, propriedades.getWebhookSecret())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(corpoBruto, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        payload.extrairMensagensDeTexto().forEach(inboundService::processarMensagemRecebida);

        return ResponseEntity.ok().build();
    }
}
