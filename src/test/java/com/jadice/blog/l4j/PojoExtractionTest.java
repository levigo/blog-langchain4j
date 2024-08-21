package com.jadice.blog.l4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * This example demonstrates how to easily extract a Java Pojo class from a user
 * input text.
 */
@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class PojoExtractionTest {

	@Value("${ollama.url}")
	private String ollamaUrl;
	@Value("${ollama.model.chat:llama3.1}")
	private String modelName;

	@Autowired
	private ObjectMapper om;

	// ### Define "Extractor" classes + beans (pojo extraction tests)

	/**
	 * Our simple pojo is a person with an address.
	 */
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

	/**
	 * This interface will be populated by lang4j AiServices mechanism. If there is
	 * only one parameter in the method, the value can be referenced in
	 * the @UserMessage Annotation with
	 * 
	 * <pre>
	 * it
	 * </pre>
	 * 
	 * (in double curly brackets).
	 */
	interface PersonExtractor {
		@UserMessage("Extract information about a person from {{it}}")
		Person extractPersonFrom(String text);
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

		logger.debug("Extracting person instance from input text\n{}", text);
		Person person = personExtractor.extractPersonFrom(text);

		// // Person { firstName = "John", lastName = "Doe",
		// birthDate = 1968-07-04,// address = Address { ... } }
		logger.info(om.writeValueAsString(person));

		assertNotNull(person);

		assertEquals("John", person.getFirstName());
		assertEquals("Doe", person.getLastName());
	}
}
