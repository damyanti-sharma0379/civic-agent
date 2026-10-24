package com.amc.civicAgent;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController
{
	private final CivicAgentService civicAgentService;

	public ComplaintController(CivicAgentService civicAgentService) {
		this.civicAgentService = civicAgentService;
	}

	/**
	 * Upload an image and area to file a civic complaint.
	 */
	@PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> fileComplaint(
				@RequestParam("photo") MultipartFile photo,
				@RequestParam("area") String area,
				@RequestParam(value = "description", required = false, defaultValue = "") String description) {

		if (photo == null || photo.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of(
						"success", false,
						"error", "Please provide a valid image file."
			));
		}

		if (area == null || area.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of(
						"success", false,
						"error", "Area name is required."
			));
		}

		try {
			String agentResponse = civicAgentService.analyzeAndFileComplaint(photo, area, description);
			return ResponseEntity.ok(Map.of(
						"success", true,
						"agentResponse", agentResponse
			));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of(
						"success", false,
						"error", "Failed to process the uploaded image: " + e.getMessage()
			));
		}
	}

	/**
	 * Check status of an existing ticket.
	 */
	@GetMapping("/status/{ticketId}")
	public ResponseEntity<Map<String, Object>> getTicketStatus(@PathVariable("ticketId") String ticketId) {
		String agentResponse = civicAgentService.checkTicketStatus(ticketId);
		return ResponseEntity.ok(Map.of(
					"success", true,
					"agentResponse", agentResponse
		));
	}
}
