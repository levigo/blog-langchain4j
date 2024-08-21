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
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic simple chat test.
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class SimpleChatTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model.chat:llama3.1}")
	private String modelName;

	/**
	 * A simple assistant with no funky stuff like memory and the like. Only a chat
	 * method to get a response for a user message.
	 */
	interface ChatBot {
		String chat(@UserMessage String message);
	}

	@Test
	public void testThat_simpleChatWorks() {
		logger.info("----- testThat_simpleChatWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model).build();

		String userMessage = "Write a 100-word poem about Java and AI";

		logger.debug("Executing user prompt: {}...", userMessage);
		String answer = assistant.chat(userMessage);

		logger.info("Answer: \n{}", answer);

		assertNotNull(answer);
		assertTrue(!answer.isEmpty(), "Answer is empty");
	}
}
