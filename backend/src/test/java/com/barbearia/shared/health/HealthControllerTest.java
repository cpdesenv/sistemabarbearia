package com.barbearia.shared.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barbearia.shared.security.JwtAuthenticationFilter;
import com.barbearia.shared.security.LoginRateLimitingFilter;

/**
 * Filtros de seguranca excluidos explicitamente desta fatia: @WebMvcTest
 * inclui automaticamente qualquer bean do tipo Filter (mesmo sem carregar o
 * SecurityConfig completo), e JwtAuthenticationFilter/LoginRateLimitingFilter
 * dependem de beans (JwtService, UsuarioRepository...) que essa fatia nao
 * carrega. O objetivo aqui e' so o payload do health check — o comportamento
 * de autenticacao/autorizacao de verdade e' coberto pelos testes de
 * integracao de autenticacao.
 */
@WebMvcTest(controllers = HealthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponderStatusUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
