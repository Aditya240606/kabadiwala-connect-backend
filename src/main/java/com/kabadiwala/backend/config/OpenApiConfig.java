package com.kabadiwala.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "SupabaseBearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Kabadiwala Connect API")
                        .description("Backend REST API for E-Waste Collection, AI-Assisted Identification, Pricing, and Recycler Handover (SIH 2026 - Problem Statement 26229)")
                        .version("1.0.0")
                        .contact(new Contact().name("Kabadiwala Connect Engineering").email("support@kabadiwalaconnect.org")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Provide valid Supabase Auth JWT token (e.g. 'Bearer eyJhbGciOi...')")));
    }
}
