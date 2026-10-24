package com.amc.civicAgent;
import com.google.genai.Client;
import com.google.genai.types.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class CivicComplaintAgent
{
	private static final String SYSTEM_INSTRUCTION = """
        You are the Municipal Civic Complaint AI Agent.
        Your goal is to help citizens file and track civic issues (potholes, garbage, broken streetlights, water logging).
        
        When a photo and an area are provided:
        1. Visually identify the issue type ('pothole', 'garbage', 'streetlight', 'water_logging') and severity.
        2. Call resolveWardAndDepartment(areaName, issueCategory) to identify the municipal jurisdiction.
        3. Call createMunicipalTicket(...) to file the complaint and generate a tracking ID.
        4. Call draftEscalationSocialPost(...) to prepare an escalation tweet/post for local authorities.
        5. Present the summary clearly: Ticket ID, Ward, Department, SLA, and the drafted social media text.
        
        When the user queries the status of an existing ticket:
        1. Call checkTicketStatus(ticketId) and return a polite, structured update.
        """;

	public static void main(String[] args) throws IOException {
		Client client = Client.builder().build(); // picks up GEMINI_API_KEY from environment
		CivicTools tools = new CivicTools();

		// 1. Define Function Declarations for Gemini
		FunctionDeclaration resolveWardFn = FunctionDeclaration.builder()
					.name("resolveWardAndDepartment")
					.description("Resolves ward ID and municipal department based on area name and issue category")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"areaName", Schema.builder()
														.type("STRING")
														.description("Locality or neighborhood")
														.build(),
											"issueCategory", Schema.builder()
														.type("STRING")
														.description("Category: pothole, garbage, streetlight, water_logging")
														.build()
								))
								.required(List.of("areaName", "issueCategory"))
								.build())
					.build();

		FunctionDeclaration createTicketFn = FunctionDeclaration.builder()
					.name("createMunicipalTicket")
					.description("Persists an official AMC ticket and generates a tracking ID")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"issueType", Schema.builder().type("STRING").build(),
											"severity", Schema.builder().type("STRING").build(),
											"description", Schema.builder().type("STRING").build(),
											"area", Schema.builder().type("STRING").build(),
											"ward", Schema.builder().type("STRING").build(),
											"department", Schema.builder().type("STRING").build()
								))
								.required(List.of("issueType", "severity", "description", "area", "ward", "department"))
								.build())
					.build();

		FunctionDeclaration checkStatusFn = FunctionDeclaration.builder()
					.name("checkTicketStatus")
					.description("Checks the current status of an existing municipal ticket ID")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"ticketId", Schema.builder()
														.type("STRING")
														.description("Tracking ID e.g. TRACK-1001")
														.build()
								))
								.required(List.of("ticketId"))
								.build())
					.build();

		FunctionDeclaration draftPostFn = FunctionDeclaration.builder()
					.name("draftEscalationSocialPost")
					.description("Generates an escalation tweet tagging the municipal corporation")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"ticketId", Schema.builder().type("STRING").build(),
											"issueType", Schema.builder().type("STRING").build(),
											"area", Schema.builder().type("STRING").build(),
											"ward", Schema.builder().type("STRING").build()
								))
								.required(List.of("ticketId", "issueType", "area", "ward"))
								.build())
					.build();

		Tool civicTools = Tool.builder()
					.functionDeclarations(List.of(resolveWardFn, createTicketFn, checkStatusFn, draftPostFn))
					.build();

		// 2. Prepare Sample Image (dummy JPEG)
		File sampleImage = new File("test_pothole.jpg");
		if (!sampleImage.exists()) {
			try (FileOutputStream fos = new FileOutputStream(sampleImage)) {
				fos.write(new byte[]{ (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, (byte)0xFF, (byte)0xDB });
			}
		}
		byte[] imageBytes = Files.readAllBytes(sampleImage.toPath());

		// 3. Conversation History
		List<Content> history = new ArrayList<>();

		System.out.println("=== Turn 1: Filing complaint with Photo + Area ===");
		Content userTurn1 = Content.builder()
					.role("user")
					.parts(List.of(
								Part.fromBytes(imageBytes, "image/jpeg"),
								Part.fromText("I found a dangerous, deep pothole in Bodakdev. Please file a complaint.")
					))
					.build();
		history.add(userTurn1);

		String agentReply1 = executeTurnWithTools(client, history, civicTools, tools);
		System.out.println(agentReply1);

		System.out.println("\n=== Turn 2: Status check ===");
		Content userTurn2 = Content.builder()
					.role("user")
					.parts(List.of(Part.fromText("Can you check the current status of TRACK-1001?")))
					.build();
		history.add(userTurn2);

		String agentReply2 = executeTurnWithTools(client, history, civicTools, tools);
		System.out.println(agentReply2);
	}

	/**
	 * Executes an agent turn, resolving model-requested function calls in a loop until a final text response is produced.
	 */
	private static String executeTurnWithTools(Client client, List<Content> history, Tool toolSpec, CivicTools tools) {
		while (true) {
			GenerateContentResponse response = client.models.generateContent(
						"gemini-3.8-flash",
						history,
						GenerateContentConfig.builder()
									.systemInstruction(Content.builder().parts(List.of(Part.fromText(SYSTEM_INSTRUCTION))).build())
									.tools(List.of(toolSpec))
									.temperature(0.2f)
									.build()
			);

			Candidate candidate = response.candidates().orElseThrow().get(0);
			Content modelContent = candidate.content().orElseThrow();
			history.add(modelContent);

			List<Part> functionCalls = (List<Part>) modelContent.parts().orElse(Collections.emptyList()).stream()
						.filter(p -> p.functionCall().isPresent());

			// If no function call, we have reached the final text response
			if (functionCalls.isEmpty()) {
				return response.text();
			}

			// Execute each requested tool call and pass the result back to Gemini
			List<Part> toolResponseParts = new ArrayList<>();
			for (Part callPart : functionCalls) {
				FunctionCall call = callPart.functionCall().get();
				String name = call.name().orElse("");
				Map<String, Object> args = call.args().orElse(Collections.emptyMap());
				Map<String, Object> executionResult = new HashMap<>();

				switch (name) {
					case "resolveWardAndDepartment" -> {
						String area = (String) args.get("areaName");
						String cat = (String) args.get("issueCategory");
						executionResult = tools.resolveWardAndDepartment(area, cat);
					}
					case "createMunicipalTicket" -> {
						executionResult = tools.createMunicipalTicket(
									(String) args.get("issueType"),
									(String) args.get("severity"),
									(String) args.get("description"),
									(String) args.get("area"),
									(String) args.get("ward"),
									(String) args.get("department")
						);
					}
					case "checkTicketStatus" -> {
						String id = (String) args.get("ticketId");
						executionResult = tools.checkTicketStatus(id);
					}
					case "draftEscalationSocialPost" -> {
						Map<String, String> post = tools.draftEscalationSocialPost(
									(String) args.get("ticketId"),
									(String) args.get("issueType"),
									(String) args.get("area"),
									(String) args.get("ward")
						);
						executionResult.putAll(post);
					}
				}

				toolResponseParts.add(Part.fromFunctionResponse(name, executionResult));
			}

			Content toolTurn = Content.builder()
						.role("tool")
						.parts(toolResponseParts)
						.build();
			history.add(toolTurn);
		}
	}
}
