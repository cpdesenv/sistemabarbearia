package com.barbearia.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import com.barbearia.auth.domain.RefreshToken;
import com.barbearia.auth.dto.LoginRequest;
import com.barbearia.auth.dto.LoginResponse;
import com.barbearia.auth.dto.UsuarioResumoMapper;
import com.barbearia.auth.repository.RefreshTokenRepository;
import com.barbearia.shared.auditoria.AuditoriaService;
import com.barbearia.shared.exception.CredenciaisInvalidasException;
import com.barbearia.shared.security.JwtProperties;
import com.barbearia.shared.security.JwtService;
import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuditoriaService auditoriaService;
    private final UsuarioResumoMapper usuarioResumoMapper;

    @Transactional
    public LoginResponse login(LoginRequest requisicao, HttpServletRequest httpRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requisicao.email(), requisicao.senha()));
        } catch (AuthenticationException ex) {
            auditoriaService.registrar(null, "LOGIN_FALHA", "usuario", null,
                    "Tentativa de login com e-mail " + requisicao.email(), httpRequest);
            throw new CredenciaisInvalidasException("E-mail ou senha invalidos.");
        }

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(requisicao.email())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha invalidos."));

        usuario.setUltimoAcessoEm(Instant.now());
        usuarioRepository.save(usuario);

        return gerarResposta(usuario, httpRequest);
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenPlano, HttpServletRequest httpRequest) {
        RefreshToken tokenAtual = refreshTokenRepository.findByTokenHash(hash(refreshTokenPlano))
                .filter(RefreshToken::isValido)
                .orElseThrow(() -> new CredenciaisInvalidasException("Refresh token invalido ou expirado."));

        tokenAtual.setRevogadoEm(Instant.now());
        refreshTokenRepository.save(tokenAtual);

        Usuario usuario = tokenAtual.getUsuario();
        if (!usuario.isAtivo()) {
            throw new CredenciaisInvalidasException("Usuario inativo.");
        }

        return gerarResposta(usuario, httpRequest);
    }

    @Transactional
    public void logout(String refreshTokenPlano) {
        refreshTokenRepository.findByTokenHash(hash(refreshTokenPlano)).ifPresent(token -> {
            token.setRevogadoEm(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    private LoginResponse gerarResposta(Usuario usuario, HttpServletRequest httpRequest) {
        String accessToken = jwtService.gerarAccessToken(usuario);
        String refreshTokenPlano = gerarTokenOpaco();
        Instant expiraEm = Instant.now().plus(jwtProperties.getRefreshTokenTtlDias(), ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(usuario, hash(refreshTokenPlano), expiraEm,
                httpRequest.getRemoteAddr());
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshTokenPlano, "Bearer", jwtService.getAccessTokenTtlSegundos(),
                usuarioResumoMapper.paraDto(usuario));
    }

    private String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 deveria estar sempre disponivel na JVM.", ex);
        }
    }
}
