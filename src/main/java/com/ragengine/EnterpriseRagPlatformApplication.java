package com.ragengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class,
        org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration.class
})
@EnableAsync
public class EnterpriseRagPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseRagPlatformApplication.class, args);
    }
}
