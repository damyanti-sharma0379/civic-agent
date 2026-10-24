package com.amc.civicAgent;

import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = { GoogleGenAiChatAutoConfiguration.class })
public class CivicAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CivicAgentApplication.class, args);
	}

}
