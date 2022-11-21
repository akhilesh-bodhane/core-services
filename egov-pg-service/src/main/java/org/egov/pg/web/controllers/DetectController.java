package org.egov.pg.web.controllers;

import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.mobile.device.Device;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class DetectController {
	
	@RequestMapping(value="/device/detect", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String detect(Device device, Model model) {

		String deviceType = null;
		if (device.isMobile()) {
			deviceType = "Mobile";
		} else if (device.isTablet()) {
			deviceType = "Tablet";
		} else {
			deviceType = "Desktop";
		}

		System.out.println("Hello User, you are viewing this applicaiton on " + deviceType);

		model.addAttribute("deviceType", deviceType);
		return JSONObject.quote(deviceType);
	}

}
