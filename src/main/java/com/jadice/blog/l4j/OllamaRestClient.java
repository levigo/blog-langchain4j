package com.jadice.blog.l4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jadice.blog.l4j.util.StringConverter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Base rest client for ollama.
 */
@Slf4j
@Component
public class OllamaRestClient {
	@Value("${ollama.url}")
	private String ollamaBaseUrl;
	private RestTemplate rest = new RestTemplate();
	@Autowired
	private ObjectMapper om;

	/**
	 * Returns a list of models currently loaded into ollama's memory.
	 * 
	 * @return the list of loaded models
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 */
	public List<String> getRunningModels() throws Exception {
		List<String> result = new ArrayList<>();
		String running = rest.getForObject(ollamaBaseUrl + "/api/ps", String.class);
		JsonNode node = om.readTree(running);

		JsonNode modelsNode = node.get("models");
		for (int i = 0; i < modelsNode.size(); i++) {
			JsonNode modelNode = modelsNode.get(i);
			result.add(modelNode.get("name").asText());
		}
		return result;
	}

	/**
	 * Returns a list of local models.
	 * 
	 * @return the list of models
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 */
	public List<ModelInfo> getModels() throws Exception {
		List<ModelInfo> result = new ArrayList<>();
		String running = rest.getForObject(ollamaBaseUrl + "/api/tags", String.class);
		JsonNode node = om.readTree(running);

		JsonNode modelsNode = node.get("models");
		for (int i = 0; i < modelsNode.size(); i++) {
			JsonNode modelNode = modelsNode.get(i);

			ModelInfo info = new ModelInfo();
			info.setName(modelNode.get("name").asText().replace(":latest", ""));

			info.setModified_at(modelNode.get("modified_at").asText());
			info.setSize(StringConverter.getBytesString(modelNode.get("size").asLong()));
			info.setDigest(modelNode.get("digest").asText());

			result.add(info);
		}
		Collections.sort(result, new Comparator<ModelInfo>() {
			@Override
			public int compare(ModelInfo o1, ModelInfo o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		return result;
	}

	/**
	 * Returns details for the given model.
	 * 
	 * @param modelName the model name
	 * @return the json details node for the model
	 * @throws JsonProcessingException
	 */
	public JsonNode getModelDetailsJson(String modelName) throws Exception {
		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("name", modelName);
//		requestParams.put("verbose", true);

		String json = om.writeValueAsString(requestParams);
		String restRes = rest.postForObject(ollamaBaseUrl + "/api/show", json, String.class);
		JsonNode result = om.readTree(restRes);

		return result;
	}

	/**
	 * Copies a model.
	 * 
	 * @param modelName    the model name
	 * @param newModelName name of the copied model
	 * @throws JsonProcessingException
	 */
	public void copyModel(String modelName, String newModelName) throws Exception {
		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("source", modelName);
		requestParams.put("destination", newModelName);

		String json = om.writeValueAsString(requestParams);
		ResponseEntity<String> restRes = rest.postForEntity(ollamaBaseUrl + "/api/copy", json, String.class);
		if (!restRes.getStatusCode().is2xxSuccessful()) {
			throw new Exception(
					"Copy failed. HTTP response " + restRes.getStatusCode().value() + ": " + restRes.getBody());
		}
	}

	/**
	 * Delete a model.
	 * 
	 * @param modelName the model to delete
	 * @throws Exception
	 */
	public void deleteModel(String modelName) throws Exception {
		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("name", modelName);

		String json = om.writeValueAsString(requestParams);
		ResponseEntity<String> restRes = deleteForEntity(ollamaBaseUrl + "/api/delete", json, String.class);
		if (!restRes.getStatusCode().is2xxSuccessful()) {
			throw new Exception(
					"Copy failed. HTTP response " + restRes.getStatusCode().value() + ": " + restRes.getBody());
		}
	}

	/**
	 * Checks if the given model is available locally in the ollama instance. If
	 * not, it will issue a pull request for the model.
	 * 
	 * @param modelName the model name
	 * @throws Exception
	 */
	public void ensureModelAvailable(String modelName) throws Exception {
		if (modelName != null && !modelName.isEmpty()) {
			ModelInfo info = null;
			for (ModelInfo m : getModels()) {
				if (Objects.equals(m.getName(), modelName)) {
					info = m;
					break;
				}
			}
			if (info == null) {
				logger.info("Pulling non available model: {}... (this might take a while)", modelName);
				pullModel(modelName);
			} else {
				logger.debug("Model available: {}", modelName);
			}
		}
	}

	/**
	 * Pulls a model.
	 * 
	 * @param modelName the model name
	 * @throws JsonProcessingException
	 */
	public void pullModel(String modelName) throws Exception {
		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("name", modelName);
		requestParams.put("stream", false);

		logger.info("Pulling Ollama model: {}", modelName);
		String json = om.writeValueAsString(requestParams);
		ResponseEntity<String> restRes = rest.postForEntity(ollamaBaseUrl + "/api/pull", json, String.class);
		if (!restRes.getStatusCode().is2xxSuccessful()) {
			throw new Exception(
					"Copy failed. HTTP response " + restRes.getStatusCode().value() + ": " + restRes.getBody());
		}
		logger.info("Pull of Ollama model finished: {}", modelName);
	}

	/**
	 * Pushes a model. Requires the ollama server to be configured with an Ollama
	 * API Key to allow pushing to ollama.com.
	 * 
	 * @param modelName the model name
	 * @throws JsonProcessingException
	 */
	public void pushModel(String modelName) throws Exception {
		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("name", modelName);
		requestParams.put("stream", false);

		logger.info("Pushing Ollama model: {}", modelName);
		String json = om.writeValueAsString(requestParams);
		ResponseEntity<String> restRes = rest.postForEntity(ollamaBaseUrl + "/api/push", json, String.class);
		if (!restRes.getStatusCode().is2xxSuccessful()) {
			throw new Exception(
					"Push failed. HTTP response " + restRes.getStatusCode().value() + ": " + restRes.getBody());
		}
		logger.info("Pushing Ollama model finished: {}", modelName);
	}

	/**
	 * Generates embeddings for the given prompt and model.
	 * 
	 * @param modelName the model, e.g. all-minilm
	 * @param prompt    the prompt to generate embeddings for
	 * @return the embeddings vector
	 * @throws Exception
	 */
	public List<Double> generateEmbeddings(String modelName, String prompt) throws Exception {
		List<Double> embeddings = new ArrayList<>();

		Map<String, Object> requestParams = new HashMap<>();
		requestParams.put("model", modelName);
		requestParams.put("prompt", prompt);

		logger.debug("Generating embeddings");
		String json = om.writeValueAsString(requestParams);
		String restRes = rest.postForObject(ollamaBaseUrl + "/api/embeddings", json, String.class);
		JsonNode result = om.readTree(restRes);
		JsonNode embeddingsNode = result.get("embedding");
		for (int i = 0; i < embeddingsNode.size(); i++) {
			embeddings.add(embeddingsNode.get(i).asDouble());
		}
		logger.debug("Generating embeddings finished");
		return embeddings;
	}

	/**
	 * Returns the model template for the given model
	 * 
	 * @param modelName the model name
	 * @return the model template
	 * @throws JsonProcessingException
	 */
	public String getModelTemplate(String modelName) throws JsonProcessingException {
		Map<String, String> values = new HashMap<>();
		values.put("name", modelName);
		String json = om.writeValueAsString(values);
		String result = rest.postForObject(ollamaBaseUrl + "/api/show", json, String.class);
		JsonNode node = om.readTree(result);
		String template = node.get("template").asText();
		return template;
	}

	private <T> ResponseEntity<T> deleteForEntity(String url, @Nullable Object request, Class<T> responseType,
			Object... uriVariables) throws RestClientException {
		RequestCallback requestCallback = rest.httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = rest.responseEntityExtractor(responseType);
		return rest.execute(url, HttpMethod.DELETE, requestCallback, responseExtractor, uriVariables);
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@ToString
	public static class ModelInfo {
		private String name;
		private String modified_at;
		private String size;
		private String digest;
	}
}
