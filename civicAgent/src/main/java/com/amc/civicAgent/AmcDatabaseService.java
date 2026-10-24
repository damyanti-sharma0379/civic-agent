package com.amc.civicAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AmcDatabaseService
{
	private static final String DB_FILE = "amc_database.json";
	private static final ObjectMapper mapper = new ObjectMapper();

	private static final Map<String, Map<String, String>> WARDS = Map.of(
				"navrangpura", Map.of("ward", "Ward-07", "zone", "West Zone"),
				"bodakdev", Map.of("ward", "Ward-12", "zone", "New West Zone"),
				"maninagar", Map.of("ward", "Ward-04", "zone", "South Zone"),
				"satellite", Map.of("ward", "Ward-11", "zone", "West Zone"),
				"paldi", Map.of("ward", "Ward-08", "zone", "West Zone")
	);

	private static final Map<String, Map<String, Object>> DEPARTMENTS = Map.of(
				"pothole", Map.of("name", "Roads & Engineering Department", "sla", 3),
				"garbage", Map.of("name", "Solid Waste Management (Sanitation)", "sla", 1),
				"streetlight", Map.of("name", "Electrical & Street Lighting", "sla", 2),
				"water_logging", Map.of("name", "Drainage & Stormwater Maintenance", "sla", 2)
	);

	public AmcDatabaseService() {
		initDb();
	}

	private synchronized void initDb() {
		File file = new File(DB_FILE);
		if (!file.exists()) {
			try {
				ObjectNode root = mapper.createObjectNode();
				ArrayNode tickets = root.putArray("tickets");
				ObjectNode sample = mapper.createObjectNode();
				sample.put("ticket_id", "TRACK-1001");
				sample.put("issue_type", "pothole");
				sample.put("severity", "High");
				sample.put("area", "Bodakdev");
				sample.put("ward", "Ward-12");
				sample.put("department", "Roads & Engineering Department");
				sample.put("status", "In Progress (Assigned to Field Crew)");
				sample.put("filed_at", "2026-10-24 10:00:00");
				tickets.add(sample);
				mapper.writerWithDefaultPrettyPrinter().writeValue(file, root);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized Map<String, Object> resolveWardAndDept(String area, String category) {
		String key = area == null ? "" : area.trim().toLowerCase();
		Map<String, String> wardInfo = WARDS.getOrDefault(key, Map.of("ward", "Ward-01 (General)", "zone", "Central Zone"));

		String catKey = category == null ? "" : category.trim().toLowerCase();
		Map<String, Object> deptInfo = DEPARTMENTS.getOrDefault(catKey, Map.of("name", "General Grievance Cell", "sla", 5));

		Map<String, Object> result = new HashMap<>();
		result.put("area", area);
		result.put("ward", wardInfo.get("ward"));
		result.put("zone", wardInfo.get("zone"));
		result.put("department", deptInfo.get("name"));
		result.put("sla_days", deptInfo.get("sla"));
		return result;
	}

	public synchronized Map<String, Object> createTicket(String issueType, String severity, String description, String area, String ward, String department) {
		String ticketId = "TRACK-" + (1000 + new Random().nextInt(9000));
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		try {
			File file = new File(DB_FILE);
			JsonNode rootNode = mapper.readTree(file);
			ArrayNode tickets = (ArrayNode) rootNode.get("tickets");

			ObjectNode newTicket = mapper.createObjectNode();
			newTicket.put("ticket_id", ticketId);
			newTicket.put("issue_type", issueType);
			newTicket.put("severity", severity);
			newTicket.put("description", description);
			newTicket.put("area", area);
			newTicket.put("ward", ward);
			newTicket.put("department", department);
			newTicket.put("status", "Registered & Dispatched to Zonal Ward Inspector");
			newTicket.put("filed_at", timestamp);

			tickets.add(newTicket);
			mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

			Map<String, Object> response = new HashMap<>();
			response.put("ticket_id", ticketId);
			response.put("status", "Registered & Dispatched to Zonal Ward Inspector");
			response.put("ward", ward);
			response.put("department", department);
			return response;
		} catch (IOException e) {
			throw new RuntimeException("Failed to save ticket to AMC DB", e);
		}
	}

	public synchronized Map<String, Object> getTicketStatus(String ticketId) {
		Map<String, Object> result = new HashMap<>();
		try {
			File file = new File(DB_FILE);
			JsonNode root = mapper.readTree(file);
			for (JsonNode t : root.get("tickets")) {
				if (t.get("ticket_id").asText().equalsIgnoreCase(ticketId.trim())) {
					result.put("found", true);
					result.put("ticket_id", t.get("ticket_id").asText());
					result.put("status", t.get("status").asText());
					result.put("issue_type", t.get("issue_type").asText());
					result.put("ward", t.get("ward").asText());
					result.put("department", t.get("department").asText());
					result.put("filed_at", t.get("filed_at").asText());
					return result;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		result.put("found", false);
		result.put("message", "Ticket " + ticketId + " not found.");
		return result;
	}

}
