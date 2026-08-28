package com.barbearia.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI barbeariaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema para Barbearia — API")
                        .description("API do sistema de gestao da barbearia (agenda, clientes, caixa, estoque, autoagendamento).")
                        .version("0.1.0"));
    }
}
