package com.barbearia.shared.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import com.barbearia.usuario.domain.Usuario;
import com.barbearia.usuario.repository.UsuarioRepository;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");

        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)) {
            String token = cabecalho.substring(PREFIXO_BEARER.length());
            autenticarSePossivel(token);
        }

        filterChain.doFilter(request, response);
    }

    private void autenticarSePossivel(String token) {
        try {
            Claims claims = jwtService.validarEExtrairClaims(token);
            UUID uuidUsuario = UUID.fromString(claims.getSubject());

            usuarioRepository.findByUuidPublico(uuidUsuario)
                    .filter(Usuario::isAtivo)
                    .ifPresent(this::autenticarNoContexto);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT invalido ou expirado: {}", ex.getMessage());
        }
    }

    private void autenticarNoContexto(Usuario usuario) {
        UsuarioAutenticado usuarioAutenticado = new UsuarioAutenticado(usuario);
        var autenticacao = new UsernamePasswordAuthenticationToken(
                usuarioAutenticado, null, usuarioAutenticado.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }
}
