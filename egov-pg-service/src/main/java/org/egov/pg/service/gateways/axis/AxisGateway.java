package org.egov.pg.service.gateways.axis;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.egov.pg.constants.PgConstants;
import org.egov.pg.models.RefundTransaction;
import org.egov.pg.models.Transaction;
import org.egov.pg.models.Transaction.TxnStatusEnum;
import org.egov.pg.service.Gateway;
import org.egov.pg.service.gateways.axis.request.GetOrderStatusRequest;
import org.egov.pg.service.gateways.axis.response.GetOrderStatusResponse;
import org.egov.pg.utils.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.extern.slf4j.Slf4j;

/**
 * AXIS Gateway implementation
 */
@Component
@Slf4j
public class AxisGateway implements Gateway {

	private static final String GATEWAY_NAME = "AXIS";
	
	private static final String AMOUNT="amount";
	private static final String CURRENCY_STR="currency";
	private static final String RECEIPT="receipt";
	private static final String KEY="key";
	private static final String ORDER_ID="order_id";
	private static final String CALLBACK_URL="callback_url";
	private static final String FAILURE="FAILURE";
	
	private final boolean ACTIVE;
	private final String CURRENCY;
	private final String MERCHANT_ID;
	private final String KEY_ID;
	private final String KEY_SECRET;

	private final RestTemplate restTemplate;
	private ObjectMapper objectMapper;
	private RazorpayClient razorpay;

	/**
	 * Initialize by populating all required config parameters
	 *
	 * @param restTemplate
	 *            rest template instance to be used to make REST calls
	 * @param environment
	 *            containing all required config parameters
	 */
	@Autowired
	public AxisGateway(RestTemplate restTemplate, Environment environment, ObjectMapper objectMapper) {
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
		
		ACTIVE = Boolean.valueOf(environment.getRequiredProperty("axis.active"));
		CURRENCY = environment.getRequiredProperty("axis.currency");
		MERCHANT_ID = environment.getRequiredProperty("axis.mid");
		KEY_ID = environment.getRequiredProperty("axis.key.id");
		KEY_SECRET = environment.getRequiredProperty("axis.key.secret");
		
		try {
			this.razorpay=new RazorpayClient(KEY_ID, KEY_SECRET);
		} catch (RazorpayException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public URI generateRedirectURI(Transaction transaction) {
		return null;
	}
	
	@Override
	public Map<String, ?> generateRedirectParameter(Transaction transaction) {
		Map<String, Object> responce=new HashMap<>();
		System.out.println("Transaction Parameters : " + transaction.toString());
		try {
			  Double amt = Double.valueOf(transaction.getTxnAmount()) * 100;
			  System.out.println("Amount : " + transaction.getTxnAmount());
			  JSONObject orderRequest = new JSONObject();
			  orderRequest.put(AMOUNT, amt); 
				/* orderRequest.put(AMOUNT, 10000); */
			  orderRequest.put(CURRENCY_STR, CURRENCY);
			  orderRequest.put(RECEIPT, transaction.getTxnId());
			  Order order = razorpay.Orders.create(orderRequest);
			  
			  System.out.println("Order Request : " + orderRequest.toString());
			  System.out.println("Order : " + order.toString());
			  
				/* responce.put(AMOUNT, Integer.valueOf(transaction.getTxnAmount())); */
			  responce.put(AMOUNT, amt);
			  responce.put(KEY, KEY_ID);
			  responce.put(ORDER_ID, order.get("id"));
			  responce.put(CALLBACK_URL, transaction.getCallbackUrl());
			  responce.put("description", transaction.getModule());
			  transaction.setGatewayTxnId(order.get("id"));
			  transaction.setTxnStatus(TxnStatusEnum.PENDING);
			  System.out.println("Response : " + responce.toString());
			  
			} catch (RazorpayException e) {
			  throw new RuntimeException(e);
			}
		return responce;
	}

	@Override
	public Transaction fetchStatus(Transaction currentStatus, Map<String, String> params) {
		return getStatusTransaction(currentStatus,params);
	}
	
	private String mapToJson(Map<String, String> map) {
		try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
	}
		
	public Transaction getStatusTransaction(Transaction currentStatus, Map<String, String> params) {
		      String razorPayId = params.get("razorpay_order_id");
		      
		      System.out.println("Razor Pay Id : " + razorPayId);
		      System.out.println("Parameters : " + params.toString());
		      
		try {
			  Order order = razorpay.Orders.fetch(razorPayId);
			  
			  if("paid".equals(order.get("status"))) {
				  return Transaction.builder().txnId(currentStatus.getTxnId())
							.txnAmount(order.get("amount_paid") + "").txnStatus(TxnStatusEnum.SUCCESS)
							.gatewayTxnId(order.get("id")).gatewayPaymentMode(params.get("method"))
							.gatewayStatusCode("")
							.gatewayStatusMsg(params.get("description")).responseJson(mapToJson(params)).build();
			  }else if("attempted".equals(order.get("status"))) {
				  return Transaction.builder().txnId(currentStatus.getTxnId())
							.txnAmount(order.get("amount_paid") + "").txnStatus(TxnStatusEnum.FAILURE)
							.gatewayTxnId(order.get("id")).gatewayPaymentMode(order.get("method"))
							.gatewayStatusCode("")
							.gatewayStatusMsg(params.get("error_description")).responseJson(mapToJson(params)).build();
			  }
			  
			  return currentStatus;
			  
			} catch (RazorpayException e) {
			  e.printStackTrace();
			  throw new RuntimeException(e);
			}
		
//		if("captured".equals(params.get("status"))) {
//			return Transaction.builder().txnId(currentStatus.getTxnId())
//					.txnAmount(params.get("amount")).txnStatus(TxnStatusEnum.FAILURE)
//					.gatewayTxnId(params.get("id")).gatewayPaymentMode(params.get("method"))
//					.gatewayStatusCode("")
//					.gatewayStatusMsg(params.get("description")).responseJson(mapToJson(params)).build();
//		}else {
//			return Transaction.builder().txnId(currentStatus.getTxnId())
//					.txnAmount(params.get("amount")).txnStatus(TxnStatusEnum.SUCCESS)
//					.gatewayTxnId(params.get("id")).gatewayPaymentMode(params.get("method"))
//					.gatewayStatusCode("")
//					.gatewayStatusMsg(params.get("error_description")).responseJson(mapToJson(params)).build();
//		}
		
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
		return "";
	}

	@Override
	public RefundTransaction initiateRefund(RefundTransaction transaction) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RefundTransaction fetchRefundStatus(RefundTransaction currentStatus) {
		// TODO Auto-generated method stub
		return null;
	}
}
