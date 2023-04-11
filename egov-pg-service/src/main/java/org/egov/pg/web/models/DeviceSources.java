package org.egov.pg.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DeviceSources {

	@JsonProperty("tenantId")
	private String tenantId;

	@JsonProperty("moduleCode")
	private String moduleCode;

	@JsonProperty("sourceUuid")
	private String sourceUuid;

	@JsonProperty("moduleType")
	private String moduleType;

	@JsonProperty("deviceType")
	private String deviceType;

	@JsonProperty("deviceDetails")
	private String deviceDetails;
	
	@JsonProperty("reference_uuid")
	private String reference_uuid;

	@JsonProperty("createdBy")
	private String createdBy;

	@JsonProperty("createdTime")
	private Long createdTime;
	
	@JsonProperty("logoutTime")
	private int logoutTime = 15;
}
