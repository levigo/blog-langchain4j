package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Tests RAG with Postgres PGVector. One test uses internal Testcontainers, the
 * other test for external postgres DB is disabled by default. Enable if needed
 * (see the /docker/docker-compose.yaml to start a pgvector DB "externally").
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class RAGChatPGVectorTest {

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
	public void testThat_ragWorksPgVector() throws JsonProcessingException {
		logger.info("----- testThat_ragWorksPgVector");

		DockerImageName dockerImageName = DockerImageName.parse("pgvector/pgvector:pg16");
		try (PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(dockerImageName)) {
			postgreSQLContainer.start();

			EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
					.host(postgreSQLContainer.getHost()).port(postgreSQLContainer.getFirstMappedPort())
					.database(postgreSQLContainer.getDatabaseName()).user(postgreSQLContainer.getUsername())
					.password(postgreSQLContainer.getPassword()).table("test").dimension(384).build();

			OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
					.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

			// Load documents for RAG
			logger.info("Loading RAG documents");
			List<Document> documents = FileSystemDocumentLoader
					.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
			logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());
			EmbeddingStoreIngestor.ingest(documents, embeddingStore);
			logger.info("RAG documents loaded. Calling assistant...");

			ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model)
					.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
					.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

			String answer = assistant.chat("who is Nelly?");

			logger.info("RAG answer: {}", answer);

			assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
			assertTrue(answer.toLowerCase().contains("dog") || answer.toLowerCase().contains("golden retriever"),
					"Not a dog");
		}
	}

	@Test
	@Disabled // run locally if external pgvector DB is present. Otherwise use
	// testcontainers. See folder /docker for an example docker-compose.yaml
	public void testThat_ragWorksPgVectorExternal() throws JsonProcessingException {
		logger.info("----- testThat_ragWorksPgVectorExternal");

		EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder().host("localhost").port(5432)
				.database("vectordb").user("postgres").password("postgres").table("test_rag_ext").dimension(384)
				.build();

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		// Load documents for RAG (only uncomment one time to insert if needed)
//		logger.info("Loading RAG documents");
//		List<Document> documents = FileSystemDocumentLoader
//				.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
//		logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());
//		EmbeddingStoreIngestor.ingest(documents, embeddingStore);

		logger.info("Calling assistant...");

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model)
				.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
				.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

		String answer = assistant.chat("who is Nelly?");

		logger.info("RAG answer: {}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog") || answer.toLowerCase().contains("golden retriever"),
				"Not a dog");
	}
}
