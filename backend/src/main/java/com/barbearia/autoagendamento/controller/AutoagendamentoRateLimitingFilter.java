package com.barbearia.autoagendamento.controller;

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
 * Limita por IP o POST /api/autoagendamento (unico endpoint que escreve
 * dado nessa rota publica) — mesmo padrao Bucket4j do
 * {@code LoginRateLimitingFilter}.
 */
@Component
public class AutoagendamentoRateLimitingFilter extends OncePerRequestFilter {

    private static final String CAMINHO_LIMITADO = "/api/autoagendamento";

    private final ConcurrentMap<String, Bucket> baldesPorIp = new ConcurrentHashMap<>();
    private final Propriedades propriedades;

    public AutoagendamentoRateLimitingFilter(Propriedades propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean limitado = "POST".equalsIgnoreCase(request.getMethod())
                && CAMINHO_LIMITADO.equals(request.getRequestURI());
        if (!limitado) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolver(request);
        Bucket balde = baldesPorIp.computeIfAbsent(ip, chave -> criarBalde());

        if (balde.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            ErroRespostaEscritor.escrever(response, 429, "LIMITE_DE_REQUISICOES_EXCEDIDO",
                    "Muitas requisicoes. Aguarde um instante e tente novamente.", request.getRequestURI());
        }
    }

    private Bucket criarBalde() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(propriedades.getRequisicoes())
                .refillIntervally(propriedades.getRequisicoes(), Duration.ofMinutes(propriedades.getJanelaMinutos()))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }

    @Component
    @ConfigurationProperties(prefix = "app.rate-limit.autoagendamento")
    @Getter
    @Setter
    public static class Propriedades {
        private int requisicoes = 10;
        private int janelaMinutos = 1;
    }
}
