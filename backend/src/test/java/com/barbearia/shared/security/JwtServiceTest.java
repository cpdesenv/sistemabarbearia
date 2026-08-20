package com.barbearia.shared.security;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;

import com.barbearia.usuario.domain.Perfil;
import com.barbearia.usuario.domain.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtProperties propriedades = propriedadesDeTeste();
    private final JwtService jwtService = new JwtService(propriedades);

    @Test
    void deveGerarTokenComClaimsEsperadas() {
        Usuario usuario = usuarioDeTeste();

        String token = jwtService.gerarAccessToken(usuario);
        Claims claims = jwtService.validarEExtrairClaims(token);

        assertThat(claims.getSubject()).isEqualTo(usuario.getUuidPublico().toString());
        assertThat(claims.get("email")).isEqualTo(usuario.getEmail());
        assertThat(claims.get("perfil")).isEqualTo("ADMIN");
    }

    @Test
    void deveRejeitarTokenExpirado() throws InterruptedException {
        propriedades.setAccessTokenTtlMinutos(0);
        JwtService jwtServiceComTtlZero = new JwtService(propriedades);
        String token = jwtServiceComTtlZero.gerarAccessToken(usuarioDeTeste());

        Thread.sleep(1_100);

        assertThatThrownBy(() -> jwtServiceComTtlZero.validarEExtrairClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private Usuario usuarioDeTeste() {
        Usuario usuario = new Usuario();
        usuario.setNome("Admin de Teste");
        usuario.setEmail("admin@teste.com");
        usuario.setPerfil(Perfil.ADMIN);
        usuario.setAtivo(true);
        return usuario;
    }

    private JwtProperties propriedadesDeTeste() {
        JwtProperties propriedades = new JwtProperties();
        propriedades.setSecret("segredo-de-teste-com-pelo-menos-32-bytes-0123456789");
        propriedades.setAccessTokenTtlMinutos(15);
        propriedades.setRefreshTokenTtlDias(7);
        return propriedades;
    }
}
