package com.barbearia.shared.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;

import com.barbearia.shared.exception.ErroRespostaEscritor;
import com.barbearia.shared.web.ClientIpResolver;

/**
 * Limita tentativas de login por IP, em memoria (sem Redis — desnecessario
 * neste porte, ver secao 2.3 do escopo). Protege contra forca bruta na unica
 * rota que aceita credenciais sem autenticacao previa.
 */
@Component
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final String CAMINHO_LOGIN = "/api/auth/login";

    private final ConcurrentMap<String, Bucket> baldesPorIp = new ConcurrentHashMap<>();
    private final Propriedades propriedades;

    public LoginRateLimitingFilter(Propriedades propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolver(request);
        Bucket balde = baldesPorIp.computeIfAbsent(ip, chave -> criarBalde());

        if (balde.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            ErroRespostaEscritor.escrever(response, 429, "LIMITE_DE_TENTATIVAS_EXCEDIDO",
                    "Muitas tentativas de login. Aguarde um instante e tente novamente.", CAMINHO_LOGIN);
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && CAMINHO_LOGIN.equals(request.getRequestURI());
    }

    private Bucket criarBalde() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(propriedades.getTentativas())
                .refillIntervally(propriedades.getTentativas(), Duration.ofMinutes(propriedades.getJanelaMinutos()))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }

    @Component
    @ConfigurationProperties(prefix = "app.rate-limit.login")
    @Getter
    @Setter
    public static class Propriedades {
        private int tentativas = 5;
        private int janelaMinutos = 1;
    }
}
