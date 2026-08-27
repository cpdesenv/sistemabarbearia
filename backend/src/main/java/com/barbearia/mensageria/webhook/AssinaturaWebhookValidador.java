package com.barbearia.mensageria.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Valida a assinatura {@code X-Hub-Signature-256} enviada pela Cloud API
 * (HMAC-SHA256 do corpo bruto da requisicao, com o segredo do webhook). Uma
 * requisicao sem essa assinatura, ou com assinatura invalida, e rejeitada
 * com 403 antes de qualquer processamento (ver WhatsAppWebhookController).
 */
public final class AssinaturaWebhookValidador {

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO = "sha256=";

    private AssinaturaWebhookValidador() {
    }

    public static boolean valida(String corpoBruto, String cabecalhoAssinatura, String segredo) {
        if (corpoBruto == null || cabecalhoAssinatura == null || !cabecalhoAssinatura.startsWith(PREFIXO)) {
            return false;
        }

        String assinaturaRecebida = cabecalhoAssinatura.substring(PREFIXO.length());
        String assinaturaEsperada = calcular(corpoBruto, segredo);
        return MessageDigest.isEqual(
                assinaturaRecebida.getBytes(StandardCharsets.UTF_8),
                assinaturaEsperada.getBytes(StandardCharsets.UTF_8));
    }

    private static String calcular(String corpoBruto, String segredo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] hash = mac.doFinal(corpoBruto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Falha ao calcular assinatura HMAC do webhook.", e);
        }
    }
}
