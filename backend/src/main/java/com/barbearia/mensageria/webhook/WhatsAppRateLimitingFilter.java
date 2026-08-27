package com.barbearia.mensageria.webhook;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
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
 * Limita por IP as chamadas ao webhook do WhatsApp e ao endpoint de injecao
 * do simulador — mesmo padrao do {@code LoginRateLimitingFilter} (bucket4j
 * em memoria, sem Redis).
 */
@Component
public class WhatsAppRateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> CAMINHOS_LIMITADOS = Set.of("/api/webhook/whatsapp", "/api/dev/whatsapp/inbound");

    private final ConcurrentMap<String, Bucket> baldesPorIp = new ConcurrentHashMap<>();
    private final Propriedades propriedades;

    public WhatsAppRateLimitingFilter(Propriedades propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!CAMINHOS_LIMITADOS.contains(request.getRequestURI())) {
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
    @ConfigurationProperties(prefix = "app.rate-limit.whatsapp")
    @Getter
    @Setter
    public static class Propriedades {
        private int requisicoes = 60;
        private int janelaMinutos = 1;
    }
}
