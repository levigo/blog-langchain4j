package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Image recognition tests using the "llava" model (https://ollama.com/library/llava).
 */
@Slf4j
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE)
public class ImageRecognitionTest {

  @Value("${ollama.url}")
  private String ollamaUrl;
  @Value("${ollama.model.image:llava}")
  private String modelName;

  @Test
  public void testThat_imageRecognitionWorks() {
    logger.info("----- testThat_imageRecognitionWorks");

    String ollamaUrl = "http://localhost:11434";
    String modelName = "llava";

    OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName).timeout(
        Duration.ofMinutes(5)).temperature(0.0).build();

    String prompt = "What do you see?";

    UserMessage userMessage = UserMessage.from(TextContent.from(prompt),
        ImageContent.from(new File(System.getProperty("user.dir"), "/src/test/resources/images/parrot.jpg").toURI()));

    logger.debug("Sending image request...");
    Response<AiMessage> response = model.generate(userMessage);

    String answer = response.content().text();

    assertTrue(answer.toLowerCase().contains("bird") || answer.toLowerCase().contains("parrot"),
        "Bird/parrot not recognized");
  }
}
