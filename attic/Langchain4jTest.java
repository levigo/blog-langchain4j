package de.wink.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever.EmbeddingStoreContentRetrieverBuilder;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class Langchain4jTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model:llama3.1}")
	private String modelName;

	@Autowired
	private ObjectMapper om;

	// ### Define "Assistant" interfaces here which are used in tests

	/**
	 * An assistant with a chat memory which can be accessed via its memoryId number
	 * (e.g. user id)
	 */
	interface AssistantWithMemoryId {
		String chat(@MemoryId int memoryId, @UserMessage String message);
	}

	/**
	 * A simple assistant with no funky stuff like memory and the like. Only a chat
	 * method to get a response for a user message.
	 */
	interface SimpleAssistant {
		String chat(@UserMessage String message);
	}

	/**
	 * This assistant will add the source of knowledge to the response
	 */
	interface RagAssistant {
		@SystemMessage("Always provide the source of knowledge")
		String chat(@UserMessage String message);
	}

	/**
	 * An assistant whose answers will always end with "powered by MadGPT"
	 */
	interface AssistantWithSystemMessage {
		@SystemMessage("You are MadGPT. All of your answers must end with 'powered by MadGPT'")
		String chat(@UserMessage String message);
	}

	// ### Define "Tools" (function calling tests)
	class Tools {
		@Tool("Adds two values, returning the sum")
		int add(int a, int b) {
			logger.info("# Calling tool function add() with parameters {} + {}", a, b);
			return a + b;
		}

		@Tool("Multiply two values")
		int multiply(int a, int b) {
			logger.info("# Calling tool function multiply() with parameters {} * {}", a, b);
			return a * b;
		}

		@Tool("Compute the kwigglydiggly value of a number")
		int computeKwigglydiggly(double a) {
			logger.info("# Calling tool function computeKwigglydiggly() with parameters {}", a);
			return ((Long) Math.round(a * 42d)).intValue();
		}

//		@Tool("Get the product list for a user name")
//		String[] productListForUserName(@P("The user name to get the product list for") String userName) {
//			logger.info("# Calling tool function productList() for userID: {}", userName);
//			Map<String, String[]> data = new HashMap<>();
//			data.put("Fritz", new String[] { "Product A", "Product B" });
//			data.put("madgpt", new String[] { "MadGPT", "Product C" });
//			return data.containsKey(userName) ? data.get(userName) : new String[] {};
//		}

		@Tool("Get the user ID for a user name")
		int userIdForName(String userName) {
			logger.info("# Calling tool function userID() for userName: {}", userName);
			Map<String, Integer> data = new HashMap<>();
			data.put("Fritz", 1);
			data.put("madgpt", 2);
			return data.get(userName);
		}

		@Tool("Get the product list for a user ID")
		String[] productListForUserID(int userID) {
			logger.info("# Calling tool function productList() for userID: {}", userID);
			Map<Integer, String[]> data = new HashMap<>();
			data.put(1, new String[] { "Product A", "Product B" });
			data.put(2, new String[] { "MadGPT", "Product C" });
			return data.containsKey(userID) ? data.get(userID) : new String[] {};
		}

	}

	// ### Define "Extractor" classes + beans (pojo extraction tests)
	@Data
	public static class Person {
		String firstName;
		String lastName;
		LocalDate birthDate;
		Address address;
	}

	@Data
	public static class Address {
		String street;
		Integer streetNumber;
		String city;
	}

	interface PersonExtractor {
		@UserMessage("Extract information about a person from {{it}}")
		Person extractPersonFrom(String text);
	}

	// ### Now to the real tests:

	@Test
	public void testThat_simpleChatWorks() {
		logger.info("----- testThat_simpleChatWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class).chatLanguageModel(model).build();

		String userMessage = "Write a 100-word poem about Java and AI";
		String answer = assistant.chat(userMessage);

		logger.info("Poem: \n{}", answer);

		assertNotNull(answer);
		assertTrue(!answer.isEmpty(), "Answer is empty");
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

	@Test
	public void testThat_chatMemoryWorks() {
		logger.info("----- testThat_chatMemoryWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		AssistantWithMemoryId assistant = AiServices.builder(AssistantWithMemoryId.class).chatLanguageModel(model)
				.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10)).build();

		String answerToFritzle = assistant.chat(1, "Hello, my name is Fritzle");
		String answerToFrancine = assistant.chat(2, "Hello, my name is Francine");

		logger.info("Answer to Fritzle: {}, Answer to Francine: {}", answerToFritzle, answerToFrancine);

		String nameOfFritzle = assistant.chat(1, "Say my name");
		String nameOfFrancine = assistant.chat(2, "Say my name");

		logger.info("Name of Fritzle: {}, Name of Francine: {}", nameOfFritzle, nameOfFrancine);

		assertTrue(nameOfFritzle.toLowerCase().contains("fritzle"), "Fritzle not detected");
		assertTrue(nameOfFrancine.toLowerCase().contains("francine"), "Francine not detected");
	}

	@Test
	public void testThat_pojoExtractionWorks() throws JsonProcessingException {
		logger.info("----- testThat_pojoExtractionWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).format("json").build();

		PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

		String text = """
				In 1968, amidst the fading echoes of Independence Day,
				a child named John arrived under the calm evening sky.
				This newborn, bearing the surname Doe, marked the start of a new journey.
				He was welcomed into the world at 345 Whispering Pines Avenue
				a quaint street nestled in the heart of Springfield
				an abode that echoed with the gentle hum of suburban dreams and aspirations.
				""";

		Person person = personExtractor.extractPersonFrom(text);

		// // Person { firstName = "John", lastName = "Doe",
		// birthDate = 1968-07-04,// address = Address { ... } }
		logger.info(om.writeValueAsString(person));

		assertNotNull(person);

		assertEquals("John", person.getFirstName());
		assertEquals("Doe", person.getLastName());
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
		InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
		EmbeddingStoreIngestor.ingest(documents, embeddingStore);
		logger.info("RAG documents loaded. Calling assistant...");

		SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class).chatLanguageModel(model)
				.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
				.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

		String answer = assistant.chat("who is Nelly?");

		logger.info("RAG answer: {}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
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

			SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class).chatLanguageModel(model)
					.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
					.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

			String answer = assistant.chat("who is Nelly?");

			logger.info("RAG answer: {}", answer);

			assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
			assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
		}
	}

	@Test
	// @Disabled // run locally if external pgvector is present. Otherwise use
	// testcontainers.
	public void testThat_ragWorksPgVectorExternal() throws JsonProcessingException {
		logger.info("----- testThat_ragWorksPgVectorExternal");

		EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder().host("localhost").port(5432)
				.database("vectordb").user("postgres").password("postgres").table("test_rag_ext").dimension(384)
				.build();

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		// Load documents for RAG
//		logger.info("Loading RAG documents");
//		List<Document> documents = FileSystemDocumentLoader
//				.loadDocuments(new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath());
//		logger.info("{} RAG documents loaded. Getting embeddings (this might take a while)...", documents.size());
//		EmbeddingStoreIngestor.ingest(documents, embeddingStore);
		logger.info("RAG documents loaded. Calling assistant...");

		SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class).chatLanguageModel(model)
				.chatMemory(MessageWindowChatMemory.withMaxMessages(10))
				.contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();

		String answer = assistant.chat("who is Nelly?");

		logger.info("RAG answer: {}", answer);

		assertTrue(answer.toLowerCase().contains("slow"), "Not slow");
		assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
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
		assertTrue(answer.toLowerCase().contains("dog"), "Not a dog");
	}

	@Test
	public void testThat_imageRecognitionWorks() {
		logger.info("----- testThat_imageRecognitionWorks");

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName("llava")
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		dev.langchain4j.data.message.UserMessage userMessage = dev.langchain4j.data.message.UserMessage
				.from(TextContent.from("What do you see?"), ImageContent.from(
						new File(System.getProperty("user.dir"), "/src/test/resources/images/parrot.jpg").toURI()));

		Response<AiMessage> response = model.generate(userMessage);

		String answer = response.content().text();

		logger.info("Image recognition: {}", answer);

		assertTrue(answer.toLowerCase().contains("bird") || answer.toLowerCase().contains("parrot"), "Not a bird");
	}

	@Test
	// FIXME: Not working / supported with ollama in langchain4j yet, but should be
	// soon
	// https://github.com/langchain4j/langchain4j/issues/1525
	// in quarkus first impl here:
	// https://github.com/quarkiverse/quarkus-langchain4j/pull/783
	public void testThat_functionCallingWorks() {
		logger.info("----- testThat_functionCallingWorks");

//		String modelName = "mistral:instruct";
		String modelName = "llama3.1:8b-instruct-q4_K_M";

		OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName)
				.timeout(Duration.ofMinutes(5)).temperature(0.0).build();

		SimpleAssistant assistant = AiServices.builder(SimpleAssistant.class).chatLanguageModel(model)
				.tools(new Tools()).build();
//		String answer = assistant.chat("What is 1+2 and 3*4?");
//		String answer = assistant.chat("Hi my name is Fritz! Which products are available");
//		String answer = assistant.chat("Hi my name is madgpt! Which products are available");
//		String answer = assistant.chat("Hi my name is Foobar! Which products are available");
//		String answer = assistant.chat("What is the kwigglydiggly value of pi?"); // (pi * 42) = 131.94...
		String answer = assistant.chat("Compute the kwigglydiggly value of pi and divide by 2");

		logger.info(answer);

		assertTrue(answer.toLowerCase().contains("131.94"));
	}

}
