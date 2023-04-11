package org.egov.pg.utils;

import java.util.UUID;

import org.egov.pg.web.models.DeviceSources;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Service;

import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;

@Service
public class DeviceSource {
	
	public DeviceSources getDeviceDetails(String request) {

		JSONObject deviceDetails = new JSONObject();
		UserAgent userAgent = UserAgent.parseUserAgentString(request);
		Browser bwr = userAgent.getBrowser();
		OperatingSystem os = userAgent.getOperatingSystem();

		deviceDetails.put("BrowserName", bwr.getName());
		deviceDetails.put("BrowserType", bwr.getBrowserType().getName());
		deviceDetails.put("BrowserEnginee", bwr.getRenderingEngine().name());
		deviceDetails.put("OperatingSystem", os.getName());
		deviceDetails.put("DeviceType", os.getDeviceType().getName());

		String sourceUuid = UUID.randomUUID().toString();
		DeviceSources deviceSources = DeviceSources.builder().sourceUuid(sourceUuid)
				.deviceDetails(deviceDetails.toJSONString())
				.deviceType(os.getDeviceType() == null ? "" : os.getDeviceType().getName())
				.logoutTime(15).build();

		return deviceSources;
	}

}
