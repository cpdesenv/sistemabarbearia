package com.barbearia.shared.criptografia;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriptografiaServiceTest {

    private static final String CHAVE_VALIDA = Base64.getEncoder()
            .encodeToString("chave-de-teste-com-32-bytes-ok!!".getBytes());

    @Test
    void deveCriptografarEDescriptografarDeVolta() {
        CriptografiaService servico = new CriptografiaService(CHAVE_VALIDA);
        String textoPlano = "refresh-token-super-secreto";

        String cifrado = servico.criptografar(textoPlano);

        assertThat(cifrado).isNotEqualTo(textoPlano);
        assertThat(servico.descriptografar(cifrado)).isEqualTo(textoPlano);
    }

    @Test
    void cadaChamadaDeveGerarCifradoDiferente() {
        CriptografiaService servico = new CriptografiaService(CHAVE_VALIDA);
        String textoPlano = "mesmo-valor";

        String cifrado1 = servico.criptografar(textoPlano);
        String cifrado2 = servico.criptografar(textoPlano);

        assertThat(cifrado1).isNotEqualTo(cifrado2);
        assertThat(servico.descriptografar(cifrado1)).isEqualTo(textoPlano);
        assertThat(servico.descriptografar(cifrado2)).isEqualTo(textoPlano);
    }

    @Test
    void deveRecusarChaveComTamanhoErrado() {
        String chaveCurta = Base64.getEncoder().encodeToString("chave-curta".getBytes());

        assertThatThrownBy(() -> new CriptografiaService(chaveCurta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
