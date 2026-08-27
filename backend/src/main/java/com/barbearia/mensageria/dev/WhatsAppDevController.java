package com.barbearia.mensageria.dev;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.barbearia.mensageria.gateway.MockWhatsAppGateway;
import com.barbearia.mensageria.service.MensageriaInboundService;
import com.barbearia.mensageria.webhook.WebhookPayload;
import com.barbearia.shared.exception.NegocioException;

/**
 * Simulador de WhatsApp: injeta mensagens de entrada como se viessem do
 * provedor, e arma falhas de envio sob demanda, sem nenhuma credencial
 * externa. So existe fora do perfil {@code prod} — nem o bean e criado em
 * producao (ver ApplicationContextRunner em
 * WhatsAppDevControllerDesabilitadoEmProdTest).
 */
@RestController
@RequestMapping("/api/dev")
@Profile("!prod")
@RequiredArgsConstructor
@Tag(name = "Simulador de WhatsApp (dev/staging)")
public class WhatsAppDevController {

    private final MensageriaInboundService inboundService;
    private final Optional<MockWhatsAppGateway> mockWhatsAppGateway;

    @GetMapping("/status")
    public ResponseEntity<DevStatusDto> status() {
        return ResponseEntity.ok(new DevStatusDto(true));
    }

    @PostMapping("/whatsapp/inbound")
    public ResponseEntity<Void> injetarMensagem(@Valid @RequestBody SimularMensagemInboundRequest requisicao) {
        String waMessageId = "sim-" + UUID.randomUUID();
        WebhookPayload.MensagemPayload mensagemPayload = new WebhookPayload.MensagemPayload(waMessageId,
                requisicao.telefone(), "text", new WebhookPayload.TextoPayload(requisicao.texto()));
        inboundService.processarMensagemRecebida(mensagemPayload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/whatsapp/simular-falha")
    public ResponseEntity<Void> simularFalhaNoProximoEnvio() {
        mockWhatsAppGateway.orElseThrow(
                        () -> new NegocioException("O gateway mock nao esta ativo (whatsapp.gateway != mock)."))
                .simularFalhaNoProximoEnvio();
        return ResponseEntity.noContent().build();
    }
}
