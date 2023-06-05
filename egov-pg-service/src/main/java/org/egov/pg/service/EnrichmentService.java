package org.egov.pg.service;

import static java.util.Collections.singletonMap;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.egov.common.contract.request.RequestInfo;
import org.egov.pg.constants.PgConstants;
import org.egov.pg.constants.TransactionAdditionalFields;
import org.egov.pg.models.AuditDetails;
import org.egov.pg.models.BankAccount;
import org.egov.pg.models.RefundTransaction;
import org.egov.pg.models.RefundTransactionRequest;
import org.egov.pg.models.Transaction;
import org.egov.pg.repository.BankAccountRepository;
import org.egov.pg.repository.TransactionRepository;
import org.egov.pg.web.models.TransactionCriteria;
import org.egov.pg.web.models.TransactionRequest;
import org.egov.pg.web.models.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EnrichmentService {

	private IdGenService idGenService;
	private BankAccountRepository bankAccountRepository;
	private ObjectMapper objectMapper;
	private TransactionRepository transactionRepository;

	@Autowired
	EnrichmentService(IdGenService idGenService, BankAccountRepository bankAccountRepository, ObjectMapper objectMapper,
			TransactionRepository transactionRepository) {
		this.idGenService = idGenService;
		this.bankAccountRepository = bankAccountRepository;
		this.objectMapper = objectMapper;
		this.transactionRepository = transactionRepository;
	}

	void enrichCreateTransaction(TransactionRequest transactionRequest) {
		Transaction transaction = transactionRequest.getTransaction();
		RequestInfo requestInfo = transactionRequest.getRequestInfo();

		BankAccount bankAccount = bankAccountRepository.getBankAccountsById(requestInfo, transaction.getTenantId());
		transaction.setAdditionalFields(
				singletonMap(TransactionAdditionalFields.BANK_ACCOUNT_NUMBER, bankAccount.getAccountNumber()));

		// Generate ID from ID Gen service and assign to txn object
		String txnId = idGenService.generateTxnId(transactionRequest);
		transaction.setTxnId(txnId);
		transaction.setUser(new User(requestInfo.getUserInfo()));
		transaction.setTxnStatus(Transaction.TxnStatusEnum.PENDING);
		transaction.setTxnStatusMsg(PgConstants.TXN_INITIATED);

		if (Objects.isNull(transaction.getAdditionalDetails()))
			transaction.setAdditionalDetails(objectMapper.createObjectNode());

		((ObjectNode) transaction.getAdditionalDetails()).set("taxAndPayments",
				objectMapper.valueToTree(transaction.getTaxAndPayments()));

		String uri = UriComponentsBuilder.fromHttpUrl(transaction.getCallbackUrl())
				.queryParams(new LinkedMultiValueMap<>(
						singletonMap(PgConstants.PG_TXN_IN_LABEL, Collections.singletonList(txnId))))
				.build().toUriString();
		transaction.setCallbackUrl(uri);

		AuditDetails auditDetails = AuditDetails.builder()
				.createdBy(requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : null)
				.createdTime(System.currentTimeMillis()).build();
		transaction.setAuditDetails(auditDetails);
	}

	void enrichUpdateTransaction(TransactionRequest transactionRequest, Transaction newTxn) {
		RequestInfo requestInfo = transactionRequest.getRequestInfo();
		Transaction currentTxnStatus = transactionRequest.getTransaction();
		
		System.out.println("Current Txn Status Msg : " + currentTxnStatus.getGatewayStatusCode().toString());
		System.out.println("Current Txn Status : " + currentTxnStatus.getTxnStatus().toString());

		AuditDetails auditDetails = AuditDetails.builder().createdBy(currentTxnStatus.getAuditDetails().getCreatedBy())
				.createdTime(currentTxnStatus.getAuditDetails().getCreatedTime())
				.lastModifiedBy(requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : null)
				.lastModifiedTime(System.currentTimeMillis()).build();
		newTxn.setAuditDetails(auditDetails);
		
		if(currentTxnStatus.getTxnStatus().equals(Transaction.TxnStatusEnum.PENDING) && currentTxnStatus.getGatewayStatusCode().toString().equalsIgnoreCase("402")) {
			newTxn.setTxnStatus(Transaction.TxnStatusEnum.FAILURE);
			System.out.println("New Txn Status : " + newTxn.getTxnStatus().toString());
		} else {
			newTxn.setTxnStatusMsg(currentTxnStatus.getTxnStatusMsg());
		}

		newTxn.setGatewayTxnId(currentTxnStatus.getGatewayTxnId());
		newTxn.setTxnId(currentTxnStatus.getTxnId());
		newTxn.setGateway(currentTxnStatus.getGateway());
		newTxn.setBillId(currentTxnStatus.getBillId());
		newTxn.setProductInfo(currentTxnStatus.getProductInfo());
		newTxn.setTenantId(currentTxnStatus.getTenantId());
		newTxn.setUser(currentTxnStatus.getUser());
		newTxn.setAdditionalDetails(currentTxnStatus.getAdditionalDetails());
		newTxn.setTaxAndPayments(currentTxnStatus.getTaxAndPayments());
		newTxn.setConsumerCode(currentTxnStatus.getConsumerCode());
		//newTxn.setTxnStatusMsg(currentTxnStatus.getTxnStatusMsg());
		newTxn.setReceipt(currentTxnStatus.getReceipt());

	}

	void enrichUpdateRefundTransaction(RefundTransactionRequest transactionRequest, RefundTransaction newTxn) {
		RequestInfo requestInfo = transactionRequest.getRequestInfo();
		RefundTransaction currentTxnStatus = transactionRequest.getRefundTransaction();

		AuditDetails auditDetails = AuditDetails.builder().createdBy(currentTxnStatus.getAuditDetails().getCreatedBy())
				.createdTime(currentTxnStatus.getAuditDetails().getCreatedTime())
				.lastModifiedBy(requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : null)
				.lastModifiedTime(System.currentTimeMillis()).build();
		newTxn.setAuditDetails(auditDetails);
	}

	void enrichRefundCreateTransaction(RefundTransactionRequest transactionRequest, Transaction currentTxnStatus) {
		RefundTransaction transaction = transactionRequest.getRefundTransaction();
		RequestInfo requestInfo = transactionRequest.getRequestInfo();

		TransactionCriteria criteria = TransactionCriteria.builder().txnId(transaction.getTxnId()).build();
		List<RefundTransaction> statuses = transactionRepository.fetchRefundTransactions(criteria);

		if (statuses.isEmpty()) {
			// Generate ID from ID Gen service and assign to txn object
			String txnId = idGenService.generateRefundTxnId(transactionRequest);
			transaction.setTxnRefundId(txnId);
		} else {
			transaction.setTxnRefundId(statuses.get(0).getTxnRefundId());
		}

		transaction.setGatewayTxnId(currentTxnStatus.getGatewayTxnId());
		transaction.setTxnAmount(currentTxnStatus.getTxnAmount());
		transaction.setGateway(currentTxnStatus.getGateway());

		AuditDetails auditDetails = AuditDetails.builder()
				.createdBy(requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : null)
				.createdTime(System.currentTimeMillis()).build();
		transaction.setAuditDetails(auditDetails);
	}
}
