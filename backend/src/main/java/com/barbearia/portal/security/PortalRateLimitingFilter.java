package com.barbearia.portal.security;

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
 * Limita a criacao de agendamentos pelo portal publico por IP, em memoria
 * (mesmo padrao do {@code LoginRateLimitingFilter}). Protege o unico
 * endpoint de escrita publico e sem autenticacao do sistema contra abuso.
 */
@Component
public class PortalRateLimitingFilter extends OncePerRequestFilter {

    private static final String CAMINHO_CRIAR_AGENDAMENTO = "/api/portal/agendamentos";

    private final ConcurrentMap<String, Bucket> baldesPorIp = new ConcurrentHashMap<>();
    private final Propriedades propriedades;

    public PortalRateLimitingFilter(Propriedades propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isCriarAgendamentoRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = ClientIpResolver.resolver(request);
        Bucket balde = baldesPorIp.computeIfAbsent(ip, chave -> criarBalde());

        if (balde.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            ErroRespostaEscritor.escrever(response, 429, "LIMITE_DE_TENTATIVAS_EXCEDIDO",
                    "Muitas tentativas de agendamento. Aguarde um instante e tente novamente.",
                    CAMINHO_CRIAR_AGENDAMENTO);
        }
    }

    private boolean isCriarAgendamentoRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && CAMINHO_CRIAR_AGENDAMENTO.equals(request.getRequestURI());
    }

    private Bucket criarBalde() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(propriedades.getTentativas())
                .refillIntervally(propriedades.getTentativas(), Duration.ofMinutes(propriedades.getJanelaMinutos()))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }

    @Component
    @ConfigurationProperties(prefix = "app.rate-limit.portal")
    @Getter
    @Setter
    public static class Propriedades {
        private int tentativas = 20;
        private int janelaMinutos = 1;
    }
}
