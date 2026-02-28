package com.ragengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise RAG Platform API")
                        .version("1.0.0")
                        .description("""
                                Enterprise-grade Retrieval-Augmented Generation platform.
                                
                                Upload documents (PDF, DOCX, TXT), and chat with them using AI.
                                The system chunks documents, generates vector embeddings, 
                                and uses similarity search to provide accurate, cited answers.
                                
                                **Key Features:**
                                - Document upload with async processing pipeline
                                - Intelligent text chunking with overlap
                                - Vector similarity search via pgvector
                                - Multi-turn conversations with history
                                - Source citations for transparency
                                """)
                        .contact(new Contact()
                                .name("Bojan Jaksic")
                                .url("https://github.com/bojanjaksic"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
