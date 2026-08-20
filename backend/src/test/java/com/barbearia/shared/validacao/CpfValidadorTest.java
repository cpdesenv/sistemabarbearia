package com.barbearia.shared.validacao;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfValidadorTest {

    @Test
    void deveNormalizarRemovendoPontuacao() {
        assertThat(CpfValidador.normalizar("111.444.777-35")).isEqualTo("11144477735");
    }

    @ParameterizedTest
    @ValueSource(strings = { "11144477735", "52998224725" })
    void deveAceitarCpfComDigitosVerificadoresCorretos(String cpf) {
        assertThat(CpfValidador.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "11144477736", "00000000000", "12345678901", "1234567890", "123456789012" })
    void deveRecusarCpfInvalido(String cpf) {
        assertThat(CpfValidador.valido(cpf)).isFalse();
    }

    @Test
    void deveRecusarCpfNulo() {
        assertThat(CpfValidador.valido(null)).isFalse();
    }
}
