package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * This test demonstrates the usage of a chat assistant with a memory for chat
 * history.
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class ChatMemoryTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model.chat:llama3.1}")
	private String modelName;

	/**
	 * An assistant with a chat memory which can be accessed via its memoryId number
	 * (e.g. user id)
	 */
	interface AssistantWithMemoryId {
		String chat(@MemoryId int memoryId, @UserMessage String message);
	}

	@Test
	public void testThat_chatMemoryWorks() {
		logger.info("----- testThat_chatMemoryWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		AssistantWithMemoryId assistant = AiServices.builder(AssistantWithMemoryId.class).chatLanguageModel(model)
				.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10)).build();

		// Here we use the memory-ID as kind of "session id". Fritzle has "1", Francine
		// has "2".
		// Also other data types could be used as memory ID, like String. Depends on the
		// Assistant-Interface we defined above.

		// Now test it: We have 2 different chat sessions (1 + 2) where the user say
		// their name first and the assistant shall repeat later on.

		logger.debug("Chat for Fritz ongoing...");
		String answerToFritz = assistant.chat(1, "Hello, my name is Fritz");
		logger.debug("Chat for Francine ongoing...");
		String answerToFrancine = assistant.chat(2, "Hello, my name is Francine");

		logger.info("Answer to Fritz: {}, Answer to Francine: {}", answerToFritz, answerToFrancine);

		// Now ask for the name in each session
		String nameOfFritz = assistant.chat(1, "Say my name");
		String nameOfFrancine = assistant.chat(2, "Say my name");

		logger.info("Name of Fritz: {}, Name of Francine: {}", nameOfFritz, nameOfFrancine);

		assertTrue(nameOfFritz.toLowerCase().contains("fritz"), "Fritz not detected");
		assertTrue(nameOfFrancine.toLowerCase().contains("francine"), "Francine not detected");
	}
}
