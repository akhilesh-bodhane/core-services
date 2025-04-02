/*
 * eGov suite of products aim to improve the internal efficiency,transparency,
 * accountability and the service delivery of the government  organizations.
 *
 *  Copyright (C) 2016  eGovernments Foundation
 *
 *  The updated version of eGov suite of products as by eGovernments Foundation
 *  is available at http://www.egovernments.org
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see http://www.gnu.org/licenses/ or
 *  http://www.gnu.org/licenses/gpl.html .
 *
 *  In addition to the terms of the GPL license to be adhered to in using this
 *  program, the following additional terms are to be complied with:
 *
 *      1) All versions of this program, verbatim or modified must carry this
 *         Legal Notice.
 *
 *      2) Any misrepresentation of the origin of the material is prohibited. It
 *         is required that all modified versions of this material be marked in
 *         reasonable ways as different from the original version.
 *
 *      3) This license does not grant any rights to any user of the program
 *         with regards to rights under trademark law for use of the trade names
 *         or trademarks of eGovernments Foundation.
 *
 *  In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */

package org.egov.web.notification.sms.config;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.egov.web.notification.sms.models.Priority;
import org.egov.web.notification.sms.models.Sms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import lombok.Getter;

@Component
public class SmsProperties {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SmsProperties.class);

	private static final String SMS_PRIORITY_PARAM_VALUE = "sms.%s.priority.param.value";
	private static final String SMS_EXTRA_REQ_PARAMS = "sms.extra.req.params";
	private static final String KEY_VALUE_PAIR_DELIMITER = "&";
	private static final String KEY_VALUE_DELIMITER = "=";
	private static final String PASSWORD_ENCRYPTION_ALGO = "SHA-1";
	private static final String SECURITY_KEY_HASHING_ALGO = "SHA-512";
	private static final String CHARSET = "iso-8859-1";
	private static final String TEMPLETE_KEY_VALUE_DELIMITER = "::";
	
	private static Map<String, String> templateMap = new HashMap<>();

	@Autowired
	private Environment environment;

	public MultiValueMap<String, String> getSmsRequestBody(Sms sms)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {
		String encryptedPassword = MD5(password);
		String genratedhashKey = hashGenerator(userName, smsSender, sms.getMessage(), secureKey);
		System.out.println("######### Encrypted Password ######## : " + encryptedPassword);
		System.out.println("######### Hash Key ######## : " + genratedhashKey);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add(userParameterName, userName);
		// map.add(passwordParameterName, password);
		map.add(passwordParameterName, encryptedPassword);
		map.add(senderIdParameterName, smsSender);
		map.add(mobileNumberParameterName, getMobileNumberWithPrefix(sms.getMobileNumber()));
		map.add(messageParameterName, sms.getMessage()==null?
				sms.getMessage():sms.getMessage().concat(defaultFooter));
		map.add(securekeyParameterName, genratedhashKey);
		map.add(templateParameterName, getTemplateId(sms.getMessage()));
		populateSmsPriority(sms.getPriority(), map);
		populateAdditionalSmsParameters(map);

		return map;
	}

	private void populateSmsPriority(Priority priority, MultiValueMap<String, String> requestBody) {
		if (isPriorityEnabled) {
			requestBody.add(smsPriorityParameterName, getSmsPriority(priority));
		}
	}

	private void populateAdditionalSmsParameters(MultiValueMap<String, String> map) {
		if (isExtraRequestParametersPresent()) {
			map.setAll(getExtraRequestParameters());
		}
	}

	@Value("${sms.provider.url}")
	@Getter
	private String smsProviderURL;

	@Value("${sms.sender.username}")
	private String userName;

	@Value("${sms.priority.enabled}")
	private boolean isPriorityEnabled;

	@Value("${sms.sender.password}")
	private String password;

	@Value("${sms.sender}")
	private String smsSender;

	@Value("${sms.sender.username.req.param.name}")
	private String userParameterName;

	@Value("${sms.sender.password.req.param.name}")
	private String passwordParameterName;

	@Value("${sms.priority.param.name}")
	private String smsPriorityParameterName;

	@Value("${sms.sender.req.param.name}")
	private String senderIdParameterName;

	@Value("${sms.destination.mobile.req.param.name}")
	private String mobileNumberParameterName;

	@Value("${sms.message.req.param.name}")
	private String messageParameterName;

	@Value("${mobile.number.prefix:}")
	private String mobileNumberPrefix;

	@Value("#{'${sms.error.codes}'.split(',')}")
	@Getter
	private List<String> smsErrorCodes;

	@Value("${sms.sender.securekey}")
	private String secureKey;

	@Value("${sms.sender.securekey.req.param.name}")
	private String securekeyParameterName;

	@Value("${sms.sender.templateid.param.name}")
	private String templateParameterName;
	
	@Value("${sms.default.footer}")
	private String defaultFooter;
	
	@Value("${sms.template.default.id}")
	private String defaultTemplateId;
	
	@Value("#{'${sms.template.ids}'.split('##')}")
	@Getter
	private List<String> smsTemplateIds;
	
	private String getTemplateId(String message){
		if(templateMap.isEmpty()){
			for(String kv : smsTemplateIds){
				String []keyValue = kv.split(TEMPLETE_KEY_VALUE_DELIMITER);
				templateMap.put(keyValue[0], keyValue[1]);
			}
		}
		
		String templateId = templateMap.entrySet().stream()
		  .filter(e -> message.contains(e.getKey()))
		  .map(Map.Entry::getValue)
		  .findFirst()
		  .orElse(defaultTemplateId);
		
		LOGGER.info("templateId-"+templateId);
		
		return templateId;
	}

	private String getSmsPriority(Priority priority) {
		return getProperty(String.format(SMS_PRIORITY_PARAM_VALUE, priority.toString()));
	}

	private String getMobileNumberWithPrefix(String mobileNumber) {
		return mobileNumberPrefix + mobileNumber;
	}

	private String getProperty(String propKey) {
		return this.environment.getProperty(propKey, "");
	}

	private boolean isExtraRequestParametersPresent() {
		return StringUtils.isNotBlank(getProperty(SMS_EXTRA_REQ_PARAMS));
	}

	private HashMap<String, String> getExtraRequestParameters() {
		String[] extraParameters = getProperty(SMS_EXTRA_REQ_PARAMS).split(KEY_VALUE_PAIR_DELIMITER);
		final HashMap<String, String> map = new HashMap<>();
		if (extraParameters.length > 0) {
			for (String extraParm : extraParameters) {
				String[] paramNameValue = extraParm.split(KEY_VALUE_DELIMITER);
				map.put(paramNameValue[0], paramNameValue[1]);
			}
		}
		return map;
	}

	private static String convertedToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
			int halfOfByte = (data[i] >>> 4) & 0x0F;
			int twoHalfBytes = 0;
			do {
				if ((0 <= halfOfByte) && (halfOfByte <= 9)) {
					buf.append((char) ('0' + halfOfByte));
				} else {
					buf.append((char) ('a' + (halfOfByte - 10)));
				}
				halfOfByte = data[i] & 0x0F;
			} while (twoHalfBytes++ < 1);
		}
		return buf.toString();
	}

	/****
	 * Method to convert Normal Plain Text Password to MD5 encrypted password
	 ***/

	private static String MD5(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MessageDigest md;
		md = MessageDigest.getInstance(PASSWORD_ENCRYPTION_ALGO);
		byte[] md5 = new byte[64];
		md.update(text.getBytes(CHARSET), 0, text.length());
		md5 = md.digest();
		return convertedToHex(md5);
	}

	private String hashGenerator(String userName, String senderId, String content, String secureKey)
			throws NoSuchAlgorithmException {
		StringBuffer finalString = new StringBuffer();
		finalString.append(userName.trim()).append(senderId.trim()).append(content.trim()).append(secureKey.trim());
		// logger.info("Parameters for SHA-512 : "+finalString);
		String hashGen = finalString.toString();
		StringBuffer sb = null;
		MessageDigest md;
		md = MessageDigest.getInstance(SECURITY_KEY_HASHING_ALGO);
		md.update(hashGen.getBytes());
		byte byteData[] = md.digest();
		// convert the byte to hex format method 1
		sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++) {
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		}

		return sb.toString();
	}

}
