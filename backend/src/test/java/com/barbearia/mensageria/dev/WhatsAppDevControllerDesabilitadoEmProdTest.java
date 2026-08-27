package com.barbearia.mensageria.dev;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.barbearia.mensageria.service.MensageriaInboundService;

/**
 * Prova que o simulador de WhatsApp ({@code /api/dev/**}) nao existe no
 * perfil {@code prod} — nao um "if" em runtime, o bean literalmente nao e
 * criado (ver {@link WhatsAppDevController#WhatsAppDevController}, anotado
 * {@code @Profile("!prod")}). Usa {@link ApplicationContextRunner} (sem
 * banco de dados real) porque o unico ponto em questao e a criacao do bean.
 */
class WhatsAppDevControllerDesabilitadoEmProdTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDeTeste.class, WhatsAppDevController.class);

    @Test
    void naoDeveExistirNoPerfilProd() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(WhatsAppDevController.class));
    }

    @Test
    void deveExistirForaDoPerfilProd() {
        contextRunner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(WhatsAppDevController.class));
    }

    @Configuration
    static class ConfiguracaoDeTeste {
        @Bean
        MensageriaInboundService mensageriaInboundService() {
            return Mockito.mock(MensageriaInboundService.class);
        }
    }
}
