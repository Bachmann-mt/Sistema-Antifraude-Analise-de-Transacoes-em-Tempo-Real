package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI antifraudeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("API de Análise Antifraude")
                .description("Contrato REST para análise de risco de transações em tempo real.")
                .version("v1")
                .contact(new Contact().name("Equipe Antifraude")));
    }
}
