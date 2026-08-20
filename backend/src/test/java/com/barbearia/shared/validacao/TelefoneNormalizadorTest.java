package com.barbearia.shared.validacao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefoneNormalizadorTest {

    @Test
    void deveNormalizarTelefoneComFormatacaoLocal() {
        assertThat(TelefoneNormalizador.normalizar("(19) 99999-8888")).isEqualTo("+5519999998888");
    }

    @Test
    void deveNormalizarTelefoneJaComCodigoDoPais() {
        assertThat(TelefoneNormalizador.normalizar("+55 19 99999-8888")).isEqualTo("+5519999998888");
        assertThat(TelefoneNormalizador.normalizar("5519999998888")).isEqualTo("+5519999998888");
    }

    @Test
    void deveNormalizarTelefoneFixo() {
        assertThat(TelefoneNormalizador.normalizar("(19) 3333-4444")).isEqualTo("+551933334444");
    }

    @Test
    void deveRetornarNuloParaEntradaVazia() {
        assertThat(TelefoneNormalizador.normalizar(null)).isNull();
        assertThat(TelefoneNormalizador.normalizar("  ")).isNull();
    }

    @Test
    void deveRecusarTelefoneComQuantidadeDeDigitosInvalida() {
        assertThatThrownBy(() -> TelefoneNormalizador.normalizar("12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRecusarDddInvalido() {
        assertThatThrownBy(() -> TelefoneNormalizador.normalizar("(00) 99999-8888"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
