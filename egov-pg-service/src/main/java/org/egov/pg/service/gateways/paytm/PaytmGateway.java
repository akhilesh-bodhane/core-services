package org.egov.pg.service.gateways.paytm;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.egov.pg.models.RefundTransaction;
import org.egov.pg.models.Transaction;
import org.egov.pg.service.Gateway;
import org.egov.pg.utils.Utils;
import org.egov.tracer.model.CustomException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.pg.merchant.PaytmChecksum;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaytmGateway implements Gateway {

	private static final String GATEWAY_NAME = "PAYTM";
	private final String MID;
	private final String MERCHANT_KEY;
	private final String MERCHANT_URL_DEBIT;
	private final String MERCHANT_URL_STATUS;
	private final String INDUSTRY_TYPE_ID;
	private final String CHANNEL_ID;
	private final String WEBSITE;
	private final String ORIGINAL_RETURN_URL_KEY;
	private final String GATEWAY_RETURN_URL;
	private final boolean ACTIVE;

	private final String MERCHANT_URL_REFUND;
	private final String MERCHANT_URL_REFUND_STATUS;

	private RestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	public PaytmGateway(RestTemplate restTemplate, Environment environment) {
		this.restTemplate = restTemplate;

		ACTIVE = Boolean.valueOf(environment.getRequiredProperty("paytm.active"));
		MID = environment.getRequiredProperty("paytm.merchant.id");
		MERCHANT_KEY = environment.getRequiredProperty("paytm.merchant.secret.key");
		INDUSTRY_TYPE_ID = environment.getRequiredProperty("paytm.merchant.industry.type");
		CHANNEL_ID = environment.getRequiredProperty("paytm.merchant.channel.id");
		WEBSITE = environment.getRequiredProperty("paytm.merchant.website");
		MERCHANT_URL_DEBIT = environment.getRequiredProperty("paytm.url.debit");
		MERCHANT_URL_STATUS = environment.getRequiredProperty("paytm.url.status");
		ORIGINAL_RETURN_URL_KEY = environment.getRequiredProperty("original.return.url.key");
		GATEWAY_RETURN_URL = environment.getRequiredProperty("gateway.return.url");

		MERCHANT_URL_REFUND = environment.getRequiredProperty("paytm.url.refund");
		MERCHANT_URL_REFUND_STATUS = environment.getRequiredProperty("paytm.url.refund.status");
	}

	@Override
	public URI generateRedirectURI(Transaction transaction) {
		TreeMap<String, String> paramMap = new TreeMap<>();
		paramMap.put("MID", MID);
		paramMap.put("ORDER_ID", transaction.getTxnId());
		paramMap.put("CUST_ID", transaction.getUser().getUserName());
		paramMap.put("INDUSTRY_TYPE_ID", INDUSTRY_TYPE_ID);
		paramMap.put("CHANNEL_ID", CHANNEL_ID);
		paramMap.put("TXN_AMOUNT", Utils.formatAmtAsRupee(transaction.getTxnAmount()));
		paramMap.put("WEBSITE", WEBSITE);
		paramMap.put("MERC_UNQ_REF", transaction.getModule());
		paramMap.put("EMAIL", transaction.getUser().getEmailId());
		paramMap.put("MOBILE_NO", transaction.getUser().getMobileNumber());
		paramMap.put("CALLBACK_URL", getReturnUrl(transaction.getCallbackUrl(), this.GATEWAY_RETURN_URL));

		try {

			// String checkSum =
			// CheckSumServiceHelper.getCheckSumServiceHelper().genrateCheckSum(MERCHANT_KEY,
			// paramMap);

			String checkSum = PaytmChecksum.generateSignature(paramMap, MERCHANT_KEY);

			paramMap.put("CHECKSUMHASH", checkSum);

			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			paramMap.forEach((key, value) -> params.put(key, Collections.singletonList(value)));

			log.info("Paytm Gatway Request Endpoint : " + MERCHANT_URL_DEBIT);
			log.info("Paytm Gatway Request Parametrs : " + params);
			UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(MERCHANT_URL_DEBIT).queryParams(params)
					.build().encode();
			log.info("Paytm Gatway Response : " + uriComponents);
			return uriComponents.toUri();
		} catch (Exception e) {
			log.error("Paytm Checksum generation failed", e);
			throw new CustomException("CHECKSUM_GEN_FAILED",
					"Hash generation failed, gateway redirect URI cannot be generated");
		}
	}

	@Override
	public Transaction fetchStatus(Transaction currentStatus, Map<String, String> params) {
		TreeMap<String, String> treeMap = new TreeMap<String, String>();
		treeMap.put("MID", MID);
		treeMap.put("ORDER_ID", currentStatus.getTxnId());

		try {
			String checkSum = PaytmChecksum.generateSignature(treeMap, MERCHANT_KEY);
			treeMap.put("CHECKSUMHASH", checkSum);

			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(treeMap, httpHeaders);
			ResponseEntity<PaytmResponse> response = restTemplate.postForEntity(MERCHANT_URL_STATUS, httpEntity,
					PaytmResponse.class);

			ObjectMapper mapper = new ObjectMapper();
			log.info("Paytm Status Request Body :" + mapper.writeValueAsString(treeMap));
			log.info("Paytm Status Response Body :" + mapper.writeValueAsString(response));

			return transformRawResponse(response.getBody(), currentStatus);

		} catch (RestClientException e) {
			log.error("Unable to fetch status from Paytm gateway", e);
			throw new CustomException("UNABLE_TO_FETCH_STATUS", "Unable to fetch status from Paytm gateway");
		} catch (Exception e) {
			log.error("Paytm Checksum generation failed", e);
			throw new CustomException("CHECKSUM_GEN_FAILED",
					"Hash generation failed, gateway redirect URI cannot be generated");
		}
	}

	@Override
	public boolean isActive() {
		return ACTIVE;
	}

	@Override
	public String gatewayName() {
		return GATEWAY_NAME;
	}

	@Override
	public String transactionIdKeyInResponse() {
		return "ORDERID";
	}

	private Transaction transformRawResponse(PaytmResponse resp, Transaction currentStatus) {

		Transaction.TxnStatusEnum status = Transaction.TxnStatusEnum.PENDING;

		if (resp.getStatus().equalsIgnoreCase("TXN_SUCCESS"))
			status = Transaction.TxnStatusEnum.SUCCESS;
		else if (resp.getStatus().equalsIgnoreCase("TXN_FAILURE"))
			status = Transaction.TxnStatusEnum.FAILURE;
		else if (resp.getStatus().equalsIgnoreCase("") && resp.getStatus().equalsIgnoreCase(null))
			status = Transaction.TxnStatusEnum.FAILURE;

		return Transaction.builder().txnId(currentStatus.getTxnId())
				.txnAmount(Utils.formatAmtAsRupee(resp.getTxnAmount())).txnStatus(status).gatewayTxnId(resp.getTxnId())
				.gatewayPaymentMode(resp.getPaymentMode()).gatewayStatusCode(resp.getRespCode())
				.gatewayStatusMsg(resp.getRespMsg()).responseJson(resp).build();

	}

	private String getReturnUrl(String callbackUrl, String baseurl) {
		return UriComponentsBuilder.fromHttpUrl(baseurl).queryParam(ORIGINAL_RETURN_URL_KEY, callbackUrl).build()
				.encode().toUriString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public RefundTransaction initiateRefund(RefundTransaction transaction) {
		JSONObject paytmParams = new JSONObject();
		
		JSONObject body = new JSONObject();
		body.put("mid", MID);
		body.put("txnType", "REFUND");
		body.put("orderId", transaction.getTxnId());
		body.put("txnId",transaction.getGatewayTxnId());
		body.put("refId",  transaction.getTxnId());
		body.put("refundAmount", transaction.getRefundAmount());
		try {
			String checkSum = PaytmChecksum.generateSignature(body.toString(), MERCHANT_KEY);
			JSONObject head = new JSONObject();
			head.put("signature", checkSum);
			paytmParams.put("body", body);
			paytmParams.put("head", head);
			String post_data = paytmParams.toString();
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			HttpEntity<String> httpEntity = new HttpEntity<>(post_data, httpHeaders);
			ResponseEntity<String> response = restTemplate.postForEntity(MERCHANT_URL_REFUND, httpEntity, String.class);
			JSONObject jsonObject = (JSONObject) new JSONParser().parse(response.getBody().toString());
			PaytmRefundResponse paytmRefundResponse = objectMapper.convertValue(jsonObject.get("body"), PaytmRefundResponse.class);
			transaction.setResponseJson(response.getBody());
			transaction.setAdditionalDetails(response.getBody());
			return transformRufundResponse(paytmRefundResponse, transaction);
		} catch (RestClientException e) {
			log.error("Unable to Refund from Paytm gateway", e);
			throw new CustomException("UNABLE_TO_REFUND", "Unable to Refund from Paytm gateway");
		} catch (Exception e) {
			log.error("Paytm Checksum generation failed", e);
			throw new CustomException("CHECKSUM_GEN_FAILED",
					"Hash generation failed, gateway redirect URI cannot be generated");
		}
	}

	private RefundTransaction transformRufundResponse(PaytmRefundResponse resp, RefundTransaction currentStatus) {
		currentStatus.setGatewayRefundStatusCode(resp.getResultInfo().getResultCode());
		currentStatus.setTxnStatus(resp.getResultInfo().getResultStatus());
		currentStatus.setGatewayRefundStatusMsg(resp.getResultInfo().getResultMsg());
		currentStatus.setGatewayRefundTxnId(resp.getRefundId());
		currentStatus.setTxnStatusMsg(resp.getResultInfo().getResultMsg());
		return currentStatus;
	}

	@SuppressWarnings("unchecked")
	@Override
	public RefundTransaction fetchRefundStatus(RefundTransaction transaction) {
		JSONObject paytmParams = new JSONObject();

		JSONObject body = new JSONObject();
		body.put("mid", MID);
		body.put("orderId", transaction.getTxnId());
		body.put("refId",  transaction.getTxnId());

		try {
			String checkSum = PaytmChecksum.generateSignature(body.toString(), MERCHANT_KEY);
			JSONObject head = new JSONObject();
			head.put("signature", checkSum);
			paytmParams.put("body", body);
			paytmParams.put("head", head);
			String post_data = paytmParams.toString();
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			HttpEntity<String> httpEntity = new HttpEntity<>(post_data, httpHeaders);
			ResponseEntity<String> response = restTemplate.postForEntity(MERCHANT_URL_REFUND_STATUS, httpEntity,
					String.class);
			JSONObject jsonObject = (JSONObject) new JSONParser().parse(response.getBody().toString());
			PaytmRefundResponse paytmRefundResponse = objectMapper.convertValue(jsonObject.get("body"), PaytmRefundResponse.class);
			transaction.setResponseJson(response.getBody());
			transaction.setAdditionalDetails(response.getBody());
			return transformRufundResponse(paytmRefundResponse, transaction);

		} catch (RestClientException e) {
			log.error("Unable to fetch Refund status from Paytm gateway", e);
			throw new CustomException("UNABLE_TO_FETCH_REFUND", "Unable to Fetch Refund Status from Paytm gateway");
		} catch (Exception e) {
			log.error("Paytm Checksum generation failed", e);
			throw new CustomException("CHECKSUM_GEN_FAILED",
					"Hash generation failed, gateway redirect URI cannot be generated");
		}
	}
}
