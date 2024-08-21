package com.jadice.blog.l4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * If enabled via application.yaml (ollama.model.auto-import=true), this will
 * auto-import missing models on startup.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "ollama.model.auto-import", havingValue = "true", matchIfMissing = false)
public class ModelAutoImport {

	@Autowired
	private OllamaRestClient ollama;

	@Value("${ollama.model.chat}")
	private String chatModelName;

	@Value("${ollama.model.image}")
	private String imageModelName;

	@Value("${ollama.model.instruct}")
	private String instructModelName;

	@PostConstruct
	private void init() {
		// We do this in @PostConstruct in an @Configuration class to be before the
		// @Bean creation process where the tests start.
		logger.info("Checking auto import");

		try {
			ollama.ensureModelAvailable(chatModelName);
			ollama.ensureModelAvailable(imageModelName);
			ollama.ensureModelAvailable(instructModelName);
		} catch (Exception e) {
			logger.error("Error initializing models", e);
		}
	}
}
