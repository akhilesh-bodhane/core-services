package org.egov.pg.web.controllers;

import lombok.extern.slf4j.Slf4j;

import org.egov.pg.models.RefundTransaction;
import org.egov.pg.models.RefundTransactionRequest;
import org.egov.pg.models.Transaction;
import org.egov.pg.service.GatewayService;
import org.egov.pg.service.TransactionService;
import org.egov.pg.utils.ResponseInfoFactory;
import org.egov.pg.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints to deal with all payment related operations
 */

@Slf4j
@Controller
public class TransactionsApiController {

	private final TransactionService transactionService;
	private final GatewayService gatewayService;

	@Autowired
	public TransactionsApiController(TransactionService transactionService, GatewayService gatewayService) {
		this.transactionService = transactionService;
		this.gatewayService = gatewayService;
	}

	/**
	 * Initiates a new payment transaction, on successful validation, a redirect is
	 * issued to the payment gateway.
	 *
	 * @param transactionRequest
	 *            Request containing all information necessary for initiating
	 *            payment
	 * @return Transaction that has been created
	 */
	@RequestMapping(value = "/transaction/v1/_create", method = RequestMethod.POST)
	public ResponseEntity<TransactionCreateResponse> transactionsV1CreatePost(
			@Valid @RequestBody TransactionRequest transactionRequest) {
		log.info("transactionsV1CreatePost() Create Request : " + transactionRequest);
//		Transaction transaction = transactionService.initiateTransaction(transactionRequest);
//		ResponseInfo responseInfo = ResponseInfoFactory
//				.createResponseInfoFromRequestInfo(transactionRequest.getRequestInfo(), true);
//		TransactionCreateResponse response = new TransactionCreateResponse(responseInfo, transaction);
		
		TransactionCreateResponse response = transactionService.initiateTransaction(transactionRequest);
		
		log.info("transactionsV1CreatePost() Create Response : " + response);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	/**
	 * Returns the current status of a transaction in our systems; This does not
	 * guarantee live payment gateway status.
	 *
	 * @param requestInfoWrapper
	 *            Request Info
	 * @param transactionCriteria
	 *            Search Conditions that should be matched
	 * @return List of transactions matching the search criteria
	 */
	@RequestMapping(value = "/transaction/v1/_search", method = RequestMethod.POST)
	public ResponseEntity<TransactionResponse> transactionsV1SearchPost(
			@Valid @RequestBody RequestInfoWrapper requestInfoWrapper,
			@Valid @ModelAttribute TransactionCriteria transactionCriteria) {
		transactionCriteria.setOffset(0);
		transactionCriteria.setLimit(5);
		List<Transaction> transactions = transactionService.getTransactions(transactionCriteria);
		ResponseInfo responseInfo = ResponseInfoFactory
				.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true);
		TransactionResponse response = new TransactionResponse(responseInfo, transactions);

		return new ResponseEntity<>(response, HttpStatus.OK);

	}

	/**
	 * Updates the status of the transaction from the gateway
	 *
	 * @param params
	 *            Parameters posted by the gateway
	 * @return The current transaction status of the transaction
	 */
	@RequestMapping(value = "/transaction/v1/_update", method = { RequestMethod.POST, RequestMethod.GET })
	public ResponseEntity<TransactionResponse> transactionsV1UpdatePost(
			@RequestBody RequestInfoWrapper requestInfoWrapper, @RequestParam Map<String, String> params) {
		
		System.out.println("Update Method Parameters : " + params.toString());
		
		List<Transaction> transactions = transactionService.updateTransaction(requestInfoWrapper.getRequestInfo(),
				params);
		ResponseInfo responseInfo = ResponseInfoFactory
				.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true);
		TransactionResponse response = new TransactionResponse(responseInfo, transactions);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	
	/*
	 * @RequestMapping("/transaction/v1/acknowledgement") void
	 * handleFoo(HttpServletResponse response) throws IOException {
	 * response.sendRedirect("http://localhost:3000/egov-opms/acknowledgement"); }
	 */
	
	@RequestMapping("/transaction/v1/redirect")
	public void sendRedirect(HttpServletRequest request,HttpServletResponse response) throws IOException {
	   
	String context = request.getParameter("context");
	String endpoint = request.getParameter("endpoint");
	String host = null;
	String hostRef = request.getParameter("hostRef");
	if(hostRef==null || hostRef.isEmpty()) {
	StringBuffer url = request.getRequestURL();
	String uri = request.getRequestURI();
	int idx = (((uri != null) && (uri.length() > 0)) ? url.indexOf(uri) : url.length());
	host = url.substring(0, idx); //base url
	}else {
	host = hostRef;
	}

	StringBuffer redirectUrl = new StringBuffer(host+"/");
	   redirectUrl.append(context+"/");
	   redirectUrl.append(endpoint);
	   redirectUrl.append("?");
	   Map<String, String[]> reqParam = request.getParameterMap();
	   for(Map.Entry<String, String[]> map:reqParam.entrySet()) {
	    if(!map.getKey().equalsIgnoreCase("hostRef")) {
	    redirectUrl.append(map.getKey()+"=");
	    if(map.getValue()!=null&&map.getValue().length>0)
	    redirectUrl.append(map.getValue()[0]+"&");
	    }
	   
	   }
	   System.out.println(redirectUrl);
	response.sendRedirect(redirectUrl.toString());
	 }

	/**
	 * Active payment gateways that can be used for payments
	 *
	 * @return list of active gateways that can be used for payments
	 */
	@RequestMapping(value = "/gateway/v1/_search", method = RequestMethod.POST)
	public ResponseEntity<Set<String>> transactionsV1AvailableGatewaysPost() {

		Set<String> gateways = gatewayService.getActiveGateways();
		log.debug("Available gateways : " + gateways);
		return new ResponseEntity<>(gateways, HttpStatus.OK);
	}

	@RequestMapping(value = "/transaction/v1/_refund", method = RequestMethod.POST)
	public ResponseEntity<RefundTransactionResponse> transactionsV1CreateRefundPost(
			@Valid @RequestBody RefundTransactionRequest transactionRequest) {
		log.info("transactionsV1CreateRefundPost() Create Request : " + transactionRequest);
		List<RefundTransaction> refundTransaction = transactionService.initiateRefundTransaction(transactionRequest);
		ResponseInfo responseInfo = ResponseInfoFactory
				.createResponseInfoFromRequestInfo(transactionRequest.getRequestInfo(), true);
		RefundTransactionResponse response = new RefundTransactionResponse(responseInfo, refundTransaction);
		log.info("transactionsV1CreateRefundPost() Create Response : " + response);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	/**
	 * Updates the status of the transaction from the gateway
	 *
	 * @param params
	 *            Parameters posted by the gateway
	 * @return The current transaction status of the transaction
	 */
	@RequestMapping(value = "/transaction/v1/_updateRefund", method = { RequestMethod.POST, RequestMethod.GET })
	public ResponseEntity<RefundTransactionResponse> transactionsV1RefundUpdatePost(
			@RequestBody RequestInfoWrapper requestInfoWrapper, @RequestParam Map<String, String> params) {
		List<RefundTransaction> refundTransactions = transactionService
				.updateRefundTransaction(requestInfoWrapper.getRequestInfo(), params);
		ResponseInfo responseInfo = ResponseInfoFactory
				.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true);
		RefundTransactionResponse response = new RefundTransactionResponse(responseInfo, refundTransactions);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
