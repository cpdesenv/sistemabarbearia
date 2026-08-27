package com.barbearia.mensageria.webhook;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaWebhookValidadorTest {

    private static final String SEGREDO = "segredo-de-teste";
    private static final String CORPO = "{\"entry\":[]}";

    @Test
    void deveAceitarAssinaturaValida() throws NoSuchAlgorithmException {
        String assinatura = "sha256=" + calcularHmac(CORPO, SEGREDO);

        assertThat(AssinaturaWebhookValidador.valida(CORPO, assinatura, SEGREDO)).isTrue();
    }

    @Test
    void deveRecusarAssinaturaComSegredoErrado() throws NoSuchAlgorithmException {
        String assinatura = "sha256=" + calcularHmac(CORPO, "segredo-errado");

        assertThat(AssinaturaWebhookValidador.valida(CORPO, assinatura, SEGREDO)).isFalse();
    }

    @Test
    void deveRecusarCorpoAlterado() throws NoSuchAlgorithmException {
        String assinatura = "sha256=" + calcularHmac(CORPO, SEGREDO);

        assertThat(AssinaturaWebhookValidador.valida("{\"entry\":[1]}", assinatura, SEGREDO)).isFalse();
    }

    @Test
    void deveRecusarCabecalhoAusente() {
        assertThat(AssinaturaWebhookValidador.valida(CORPO, null, SEGREDO)).isFalse();
    }

    @Test
    void deveRecusarCabecalhoSemPrefixoSha256() {
        assertThat(AssinaturaWebhookValidador.valida(CORPO, "assinatura-sem-prefixo", SEGREDO)).isFalse();
    }

    private String calcularHmac(String corpo, String segredo) throws NoSuchAlgorithmException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
