package com.jadice.blog.l4j;

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
 * Function calling test to count characters in a text.
 */
@Slf4j
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE)
public class StrawberryTest {

  @Value("${ollama.url}")
  private String ollamaUrl;
  @Value("${ollama.model.instruct}")
  private String modelName;

  interface ChatBot {
    String chat(@UserMessage String message);
  }

  @Getter
  class Tools {
    @Tool("Counts how often a character appears in a text. First parameter the text, second the character to search for")
    int countCharacters(String text, String searchFor) {
      int count = 0;
      int y = text.indexOf(searchFor);
      while (y > -1) {
        count++;
        y = text.indexOf(searchFor, y + 1);
      }
      logger.info("Counted {} {}-tokens in text {}", count, searchFor, text);
      return count;
    }
  }

  @Test
  public void testThat_characterCountingWorks() {
    logger.info("----- testThat_characterCountingWorks");

    OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaUrl).modelName(modelName).timeout(
        Duration.ofMinutes(5)).temperature(0.0).build();

    Tools tools = new Tools();

    ChatBot assistant = AiServices.builder(ChatBot.class).chatLanguageModel(model).tools(tools).build();
    String question = "How many r-characters in the word Strawberry?";
    String answer = assistant.chat(question);

    logger.info("\nQuestion:\n{}\n\nAnswer:\n{}", question, answer);

    assertTrue(answer.toLowerCase().contains("3"));
  }

}
