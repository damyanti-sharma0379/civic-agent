package com.amc.civicAgent;

import java.util.Map;
public class CivicTools
{
	private final AmcDatabaseService db = new AmcDatabaseService();


	public Map<String, Object> resolveWardAndDepartment(String areaName, String issueCategory) {
		return db.resolveWardAndDept(areaName, issueCategory);
	}


	public Map<String, Object> createMunicipalTicket(String issueType, String severity, String description, String area, String ward, String department) {
		return db.createTicket(issueType, severity, description, area, ward, department);
	}

	public Map<String, Object> checkTicketStatus(String ticketId) {
		return db.getTicketStatus(ticketId);
	}

	public Map<String, String> draftEscalationSocialPost(String ticketId, String issueType, String area, String ward) {
		String post = String.format(
					"🚨 Urgent civic attention required in %s (%s)!\n" +
								"Issue: Severe %s posing hazard to citizens & traffic.\n" +
								"AMC Complaint ID: #%s\n\n" +
								"Requesting prompt action @AmdavadAMC @AMC_Commissioner @SmartCityAMC\n" +
								"#CivicAction #CleanCity #CitizenWatch #%s",
					area, ward, issueType, ticketId, area.replaceAll("\\s+", "")
		);
		return Map.of("social_post", post);
	}
}
