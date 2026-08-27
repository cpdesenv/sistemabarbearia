package com.barbearia.mensageria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.shared.IntegrationTestBase;

/**
 * Cobre o rate limiting do webhook e do endpoint de injecao do simulador
 * isoladamente, com um limite baixo (numero de tentativas diferente do
 * resto da suite) — a propriedade dinamica forca um contexto Spring
 * proprio, com seu proprio balde de tokens, sem interferir (nem sofrer
 * interferencia) dos demais testes de {@code WhatsAppWebhookIntegrationTest}.
 * O balde e' por IP (nao por rota), entao as duas rotas compartilham o
 * mesmo limite — ver {@code WhatsAppRateLimitingFilter}.
 */
@Transactional
class WhatsAppRateLimitingIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void limiteBaixo(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.whatsapp.requisicoes", () -> 3);
        registry.add("app.rate-limit.whatsapp.janela-minutos", () -> 1);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveBloquearAposEsgotarOLimiteDeRequisicoesNoWebhookENoEndpointDeInjecao() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/webhook/whatsapp")
                            .param("hub.mode", "subscribe")
                            .param("hub.verify_token", "qualquer")
                            .param("hub.challenge", "x"))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "qualquer")
                        .param("hub.challenge", "x"))
                .andExpect(status().isTooManyRequests());

        // Mesmo IP, mesmo balde ja esgotado — o endpoint de injecao do simulador
        // tambem fica bloqueado (nao precisa de autenticacao pra isso: o filtro
        // roda antes do JWT).
        mockMvc.perform(post("/api/dev/whatsapp/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\": \"19999998888\", \"texto\": \"oi\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
