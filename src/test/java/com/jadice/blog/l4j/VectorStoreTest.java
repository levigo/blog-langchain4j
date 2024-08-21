package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Some tests for the Vector store.
 */
@Slf4j
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE)
public class VectorStoreTest {

  private static InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
  private static EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

  @BeforeAll
  public static void initLoadDocuments() {
    // Load the document that includes the information you'd like to "chat" about
    // with the model.
    logger.info("Loading RAG documents");

    List<Document> documents = new ArrayList<>();

    documents.addAll(FileSystemDocumentLoader.loadDocuments(
        new File(System.getProperty("user.dir"), "/src/test/resources/testdocs").toPath()));
    documents.addAll(FileSystemDocumentLoader.loadDocuments(
        new File(System.getProperty("user.dir"), "/src/test/resources/testdocs-large").toPath()));

    logger.info("Getting embeddings for {} RAG document(s) (this might take a while)...", documents.size());

    // Split document into segments 300 tokens each
    DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);

    // Embed segments (convert them into vectors that represent the meaning) using
    // embedding model
    for (Document document : documents) {
      logger.info("Adding {}", document.metadata().getString("file_name"));
      List<TextSegment> segments = splitter.split(document);
      List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
      embeddingStore.addAll(embeddings, segments);
    }

    logger.info("{} RAG documents loaded", documents.size());
  }

  @Test
  public void testThat_vectorRetrievalWorksSimple() {
    logger.info("----- testThat_vectorRetrievalWorksSimple");

    // Specify the question you want to ask the model
    String question = "Who is Nelly?";

    // Embed the question
    Embedding questionEmbedding = embeddingModel.embed(question).content();

    // Find relevant embeddings in embedding store by semantic similarity
    // You can play with parameters below to find a sweet spot for your specific use
    // case
    int maxResults = 3;
    double minScore = 0.7;

    // Search the closest vectors
    EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder().queryEmbedding(
        questionEmbedding).maxResults(maxResults).minScore(minScore).build();
    EmbeddingSearchResult<TextSegment> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
    List<EmbeddingMatch<TextSegment>> relevantEmbeddings = embeddingSearchResult.matches();

    String information = relevantEmbeddings.stream().map(match -> match.embedded().text()).collect(
        Collectors.joining("\n\n"));

    // See an answer from the model
    printRelevantEmbeddingInfos(relevantEmbeddings);

    assertTrue(information.toLowerCase().contains("slow"), "Not slow");
    assertTrue(information.toLowerCase().contains("dog") || information.toLowerCase().contains("golden retriever"),
        "Not a dog");
  }

  @Test
  public void testThat_vectorRetrievalWorksDB2Documents() {
    logger.info("----- testThat_vectorRetrievalWorksDB2Documents");

    // Specify the question you want to ask the model
    String question = "I am getting 'Invalid attribute with ID' error messages. What to do?";

    logger.debug("Embedding finished; executing query for: {}", question);

    // Embed the question
    Embedding questionEmbedding = embeddingModel.embed(question).content();

    // Find relevant embeddings in embedding store by semantic similarity
    // You can play with parameters below to find a sweet spot for your specific use
    // case
    int maxResults = 3;
    double minScore = 0.7;

    // Search the closest vectors
    EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder().queryEmbedding(
        questionEmbedding).maxResults(maxResults).minScore(minScore).build();
    EmbeddingSearchResult<TextSegment> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
    List<EmbeddingMatch<TextSegment>> relevantEmbeddings = embeddingSearchResult.matches();

    String information = relevantEmbeddings.stream().map(match -> match.embedded().text()).collect(
        Collectors.joining("\n\n"));

    printRelevantEmbeddingInfos(relevantEmbeddings);

    assertTrue(information.toUpperCase().contains("DGL7096A"), "DGL7096A not found");
  }

  private void printRelevantEmbeddingInfos(List<EmbeddingMatch<TextSegment>> relevantEmbeddings) {
    StringBuilder sb = new StringBuilder();

    for (EmbeddingMatch<TextSegment> result : relevantEmbeddings) {
      if (sb.length() > 0) {
        sb.append(System.lineSeparator());
      }
      sb.append(result.embedded().metadata().getString("file_name"));
      sb.append(" : Index: ");
      sb.append(result.embedded().metadata().getString("index"));
      sb.append(" -> ");

      sb.append(result.embedded().text());
    }

    // See an answer from the model
    logger.debug("Relevant RAG Vector DB information:\n{}", sb.toString());
  }

}
