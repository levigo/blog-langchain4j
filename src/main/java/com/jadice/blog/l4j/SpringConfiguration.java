package com.jadice.blog.l4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class SpringConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ObjectMapper objectMapper() {
		ObjectMapper om = new ObjectMapper();
		om.registerModule(javaTimeModule());
		return om;
	}

	@Bean
	public JavaTimeModule javaTimeModule() {
		JavaTimeModule module = new JavaTimeModule();
//		module.addSerializer(LOCAL_DATETIME_SERIALIZER);
		return module;
	}
}
