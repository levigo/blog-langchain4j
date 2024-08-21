package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates how to set a system message to modify LLM behavior.
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class SystemMessageTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model.chat:llama3.1}")
	private String modelName;

	/**
	 * An assistant whose answers will always end with "powered by MadGPT"
	 */
	interface AssistantWithSystemMessage {
		@SystemMessage("You are MadGPT. All of your answers must end with 'powered by MadGPT'")
		String chat(@UserMessage String message);
	}

	@Test
	public void testThat_systemMessageWorks() {
		logger.info("----- testThat_systemMessageWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		AssistantWithSystemMessage assistant = AiServices.builder(AssistantWithSystemMessage.class)
				.chatLanguageModel(model).build();

		String userMessage = "Write a 100-word poem about Java and AI";
		String answer = assistant.chat(userMessage);

		logger.info("Poem (and system message): \n{}", answer);

		assertNotNull(answer);
		assertTrue(!answer.isEmpty(), "Answer is empty");
		assertTrue(answer.toLowerCase().contains("powered by madgpt"), "'powered by' missing: " + answer);
	}
}
