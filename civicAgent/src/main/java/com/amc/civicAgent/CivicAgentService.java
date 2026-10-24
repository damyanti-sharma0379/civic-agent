package com.amc.civicAgent;
import com.amc.civicAgent.CivicTools;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class CivicAgentService
{
	private final Client client;
	private final CivicTools tools;
	private final Tool toolSpec;
	private static final String MODEL_NAME = "gemini-3.8-flash";

	private static final String SYSTEM_INSTRUCTION = """
        You are the Municipal Civic Complaint AI Agent.
        Your goal is to inspect photos of civic issues, route them to the correct ward and department, and file complaints.
        
        When a photo and an area are provided:
        1. Inspect the photo to visually identify the defect category ('pothole', 'garbage', 'streetlight', 'water_logging') and severity ('Low', 'Medium', 'High', 'Critical').
        2. Call resolveWardAndDepartment(areaName, issueCategory) using the provided area and identified category.
        3. Call createMunicipalTicket(...) to log the ticket and generate a tracking ID.
        4. Call draftEscalationSocialPost(...) to compose an escalation tweet tagging municipal authorities.
        5. Present the final summary: Ticket ID, Ward, Department, SLA timeline, and the drafted social media text.
        
        When checking status:
        1. Call checkTicketStatus(ticketId) and return a clear status update.
        """;

	public CivicAgentService() {
		this.client = Client.builder().build(); // Reads GEMINI_API_KEY from environment
		this.tools = new CivicTools();

		FunctionDeclaration resolveWardFn = FunctionDeclaration.builder()
					.name("resolveWardAndDepartment")
					.description("Resolves ward ID and municipal department based on area name and issue category")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"areaName", Schema.builder().type("STRING").description("Locality or area name").build(),
											"issueCategory", Schema.builder().type("STRING").description("Category: pothole, garbage, streetlight, water_logging").build()
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
					.description("Checks status of an existing ticket ID")
					.parameters(Schema.builder()
								.type("OBJECT")
								.properties(Map.of(
											"ticketId", Schema.builder().type("STRING").description("e.g. TRACK-1001").build()
								))
								.required(List.of("ticketId"))
								.build())
					.build();

		FunctionDeclaration draftPostFn = FunctionDeclaration.builder()
					.name("draftEscalationSocialPost")
					.description("Generates an escalation tweet tagging municipal handles")
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

		this.toolSpec = Tool.builder()
					.functionDeclarations(List.of(resolveWardFn, createTicketFn, checkStatusFn, draftPostFn))
					.build();
	}

	public String analyzeAndFileComplaint(MultipartFile photo, String area, String optionalDescription) throws IOException {
		String mimeType = photo.getContentType();
		if (mimeType == null || mimeType.isBlank()) {
			mimeType = "image/jpeg";
		}

		byte[] photoBytes = photo.getBytes();

		String userText = "Analyze this uploaded photo of a civic issue located in " + area.trim() + ".";
		if (optionalDescription != null && !optionalDescription.isBlank()) {
			userText += " User description: " + optionalDescription.trim();
		}

		List<Content> history = new ArrayList<>();
		history.add(Content.builder()
					.role("user")
					.parts(List.of(
								Part.fromBytes(photoBytes, mimeType),
								Part.fromText(userText)
					))
					.build());

		return executeToolLoop(history);
	}

	public String checkTicketStatus(String ticketId) {
		List<Content> history = new ArrayList<>();
		history.add(Content.builder()
					.role("user")
					.parts(List.of(Part.fromText("What is the current status of ticket " + ticketId + "?")))
					.build());

		return executeToolLoop(history);
	}

	private String executeToolLoop(List<Content> history) {
		while (true) {
			GenerateContentResponse response = client.models.generateContent(
						MODEL_NAME,
						history,
						GenerateContentConfig.builder()
									.systemInstruction(Content.builder().parts(List.of(Part.fromText(SYSTEM_INSTRUCTION))).build())
									.tools(List.of(this.toolSpec))
									.temperature(0.2f)
									.build()
			);

			Candidate candidate = response.candidates().orElseThrow().get(0);
			Content modelContent = candidate.content().orElseThrow();
			history.add(modelContent);

			List<FunctionCall> functionCalls = response.functionCalls();
			if (functionCalls == null || functionCalls.isEmpty()) {
				return response.text();
			}

			List<Part> toolResults = new ArrayList<>();
			for (FunctionCall call : functionCalls) {
				String name = call.name().orElse("");
				Map<String, Object> args = call.args().orElse(Collections.emptyMap());
				Map<String, Object> execResult = new HashMap<>();

				switch (name) {
					case "resolveWardAndDepartment" -> {
						String areaName = (String) args.get("areaName");
						String issueCat = (String) args.get("issueCategory");
						execResult = tools.resolveWardAndDepartment(areaName, issueCat);
					}
					case "createMunicipalTicket" -> {
						execResult = tools.createMunicipalTicket(
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
						execResult = tools.checkTicketStatus(id);
					}
					case "draftEscalationSocialPost" -> {
						Map<String, String> post = tools.draftEscalationSocialPost(
									(String) args.get("ticketId"),
									(String) args.get("issueType"),
									(String) args.get("area"),
									(String) args.get("ward")
						);
						execResult.putAll(post);
					}
				}

				toolResults.add(Part.fromFunctionResponse(name, execResult));
			}

			history.add(Content.builder().role("tool").parts(toolResults).build());
		}
	}
}
