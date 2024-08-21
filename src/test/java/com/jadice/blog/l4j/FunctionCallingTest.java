package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Function calling tests. We provide some methods to compute math and a
 * "kwigglydiggly" value for a number and see if the LLM calls the functions.
 * <p>
 * 
 * <b>NOTE</b><br>
 * You need langchain4j >= 0.34.0 for this test to work. As time of writing it
 * is not released yet - in this case the current langchain4j-project can be
 * checked out locally for the 0.34.0-SNAPSHOT.
 * <p>
 * https://github.com/langchain4j/langchain4j/issues/1525
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class FunctionCallingTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model.instruct}")
	private String modelName;

	/**
	 * A simple assistant with no funky stuff like memory and the like. Only a chat
	 * method to get a response for a user message.
	 */
	interface ChatBot {
		String chat(@UserMessage String message);
	}

	/**
	 * Simple Tools for this test. Each @Tool method will also set a boolean whether
	 * it was called. Can be used in tests to check if a tool was used.
	 */
	@Getter
	class Tools {
		boolean kwigglydigglyCalled = false;
		boolean addCalled = false;
		boolean multiplyCalled = false;

		@Tool("Adds two values, returning the sum")
		int add(int a, int b) {
			addCalled = true;
			logger.info("# Calling tool function add() with parameters {} + {}", a, b);
			return a + b;
		}

		@Tool("Multiply two values")
		int multiply(int a, int b) {
			multiplyCalled = true;
			logger.info("# Calling tool function multiply() with parameters {} * {}", a, b);
			return a * b;
		}

		@Tool("Compute the kwigglydiggly value of a number")
		int computeKwigglydiggly(double a) {
			kwigglydigglyCalled = true;
			// In the end, the "kwigglydiggly" value is the original value * 42; rounded to
			// int
			logger.info("# Calling tool function computeKwigglydiggly() with parameters {}", a);
			return ((Long) Math.round(a * 42d)).intValue();
		}

	}

	@Test
	public void testThat_functionCallingKwigglyDigglySimpleWorks() {
		logger.info("----- testThat_functionCallingKwigglyDigglySimpleWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		Tools tools = new Tools();

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model).tools(tools).build();
		String answer = assistant.chat("What is the kwigglydiggly value of pi?"); // (pi * 42) = 131.94...

		logger.info(answer);

		assertTrue(answer.toLowerCase().contains("132"));
		assertTrue(tools.isKwigglydigglyCalled());
	}

	@Test
	public void testThat_functionCallingKwigglyDigglyComplexNotWorks() {
		logger.info("----- testThat_functionCallingKwigglyDigglyComplexNotWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model).tools(new Tools()).build();
		try {
			String answer = assistant.chat("Compute the kwigglydiggly value of pi and multiply by 2.0");

			logger.info(answer);

			// does not work usually...
			assertFalse(answer.toLowerCase().contains("264"),
					"Wow, they fixed it. Assumed not to get the right result");
		} catch (Exception e) {
			// yep
			logger.info("Exception as expected ({})", e.getMessage());
		}
	}

	@Test
	public void testThat_functionCallingMathWorks() {
		logger.info("----- testThat_functionCallingMathWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		Tools tools = new Tools();

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model).tools(tools).build();
		String answer = assistant.chat("What is 1+2 and 3*4?");

		logger.info(answer);

		assertTrue(answer.toLowerCase().contains("3"));
		assertTrue(answer.toLowerCase().contains("12"));
		assertTrue(tools.isAddCalled());
		assertTrue(tools.isMultiplyCalled());
		assertFalse(tools.isKwigglydigglyCalled());
	}
}
