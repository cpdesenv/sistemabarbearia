package com.barbearia.shared.criptografia;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Criptografia simetrica reversivel (AES-256-GCM) para segredos que precisam
 * ser recuperados depois (ex.: refresh token do Google Calendar) — diferente
 * do hash de uso unico usado em {@code RefreshToken} (auth), que so precisa
 * ser comparado, nunca lido de volta.
 */
@Component
public class CriptografiaService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_IV_BYTES = 12;

    private final SecretKeySpec chave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CriptografiaService(@Value("${app.calendar.token-encryption-key}") String chaveBase64) {
        byte[] bytes = Base64.getDecoder().decode(chaveBase64);
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "app.calendar.token-encryption-key deve decodificar para exatamente 32 bytes (AES-256).");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public String criptografar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            aleatorio.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar segredo.", e);
        }
    }

    public String descriptografar(String textoCifradoBase64) {
        try {
            byte[] dados = Base64.getDecoder().decode(textoCifradoBase64);
            byte[] iv = Arrays.copyOfRange(dados, 0, TAMANHO_IV_BYTES);
            byte[] cifrado = Arrays.copyOfRange(dados, TAMANHO_IV_BYTES, dados.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao descriptografar segredo.", e);
        }
    }
}
