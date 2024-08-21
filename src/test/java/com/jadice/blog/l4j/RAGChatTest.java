package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever.EmbeddingStoreContentRetrieverBuilder;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple RAG chat tests with different API approaches.
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class RAGChatTest {

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

	/**
	 * This assistant will add the source of knowledge to the response
	 */
	interface RagAssistant {
		@SystemMessage("Always provide the source of knowledge")
		String chat(@UserMessage String message);
	}

	@Test
	public void testThat_ragWorks() throws JsonProcessingException {
		logger.info("----- testThat_ragWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		// Load documents for RAG
		logger.info("Loading RAG documents");
		List<Document> documents = FileSystemDocumentLoader
				.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
		logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());

		// We are using the in-memory Vector store here
		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
		EmbeddingStoreIngestor.ingest(documents, embeddingStore);
		logger.info("RAG documents loaded. Calling assistant...");

		ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model)
				.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
				.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

		String answer = assistant.chat("who is Nelly?");

		logger.info("RAG answer: {}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
	}

	@Test
	public void testThat_ragWorks_lowLevel() {
		logger.info("----- testThat_ragWorks_lowLevel");

		// Load the document that includes the information you'd like to "chat" about
		// with the model.
		logger.info("Loading RAG documents");

		List<Document> documents = FileSystemDocumentLoader
				.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
		logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());

		// Split document into segments 300 tokens each
		DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);

		// Embed segments (convert them into vectors that represent the meaning) using
		// embedding model
		EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

		// Store embeddings into embedding store for further search / retrieval
		EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		for (Document document : documents) {
			List<TextSegment> segments = splitter.split(document);
			List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
			embeddingStore.addAll(embeddings, segments);
		}

		// Specify the question you want to ask the model
		String question = "Who is Nelly?";

		// Embed the question
		Embedding questionEmbedding = embeddingModel.embed(question).content();

		// Find relevant embeddings in embedding store by semantic similarity
		// You can play with parameters below to find a sweet spot for your specific use
		// case
		int maxResults = 3;
		double minScore = 0.7;

		EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
				.queryEmbedding(questionEmbedding).maxResults(maxResults).minScore(minScore).build();
		EmbeddingSearchResult<TextSegment> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
		List<EmbeddingMatch<TextSegment>> relevantEmbeddings = embeddingSearchResult.matches();

		// Create a prompt for the model that includes question and relevant embeddings
		PromptTemplate promptTemplate = PromptTemplate.from("""
				Answer the following question to the best of your ability:

				Question:
				{{question}}

				Base your answer on the following information:
				{{information}}
				""");

		String information = relevantEmbeddings.stream().map(match -> match.embedded().text())
				.collect(Collectors.joining("\n\n"));

		Map<String, Object> variables = new HashMap<>();
		variables.put("question", question);
		variables.put("information", information);

		Prompt prompt = promptTemplate.apply(variables);

		// Send the prompt to the OpenAI chat model
		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		AiMessage aiMessage = model.generate(prompt.toUserMessage()).content();

		// See an answer from the model
		String answer = aiMessage.text();
		logger.debug("RAG low level answer:\n{}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog") || answer.toLowerCase().contains("golden retriever"),
				"Not a dog");
	}

	/**
	 * Advanced RAG test where also metadata (file name) will be appended to the
	 * answer.
	 */
	@Test
	public void testThat_ragWorks_advanced() {
		logger.info("----- testThat_ragWorks_advanced");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		// Load documents for RAG
		logger.info("Loading RAG documents");
		List<Document> documents = FileSystemDocumentLoader
				.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
		logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());
		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		EmbeddingStoreIngestor.ingest(documents, embeddingStore);
		// from rag-simple; since the one from the EmbeddingStoreIngestor is private...
		EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
		EmbeddingStoreContentRetrieverBuilder builder = EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore).embeddingModel(embeddingModel);

		ContentRetriever contentRetriever = builder.build();
		// Each retrieved segment should include "file_name" and "index" metadata values
		// in the prompt
		ContentInjector contentInjector = DefaultContentInjector.builder()
				// .promptTemplate(...) // Formatting can also be changed
				.metadataKeysToInclude(Arrays.asList("file_name", "index")).build();

		RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder().contentRetriever(contentRetriever)
				.contentInjector(contentInjector).build();

		RagAssistant assistant = AiServices.builder(RagAssistant.class).chatLanguageModel(model)
				.retrievalAugmentor(retrievalAugmentor).chatMemory(MessageWindowChatMemory.withMaxMessages(10)).build();

		String answer = assistant.chat("who is Nelly?");

		logger.info("RAG advanced answer: {}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
		assertTrue(answer.toLowerCase().contains("nelly.txt"), "Did not find RAG source");
	}
}
