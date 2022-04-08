package org.egov.pg.config;

import lombok.Getter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Getter
@ToString
@Configuration
@PropertySource("classpath:application.properties")
public class AppProperties {

	private final Integer earlyReconcileJobRunInterval;

	private final String saveTxnTopic;

	private final String updateTxnTopic;
	
	//Added for axis bank recon update
	private final String updateTxnTopicAxis;

	private final String saveTxnDumpTopic;

	private final String updateTxnDumpTopic;
	
	//Added for axis bank recon update
	private final String updateTxnDumpTopicAxis;

	private final String saveRefundTxnTopic;

	private final String idGenHost;

	private final String idGenPath;

	private final String idGenName;

	private final String idGenFormat;

	private final String idGenRefundFormat;

	private final String collectionServiceHost;

	private final String collectionServiceCreatePath;

	private final String collectionServiceValidatePath;

	private final String paymentCreatePath;

	private final String paymentValidatePath;

	private final String bankAccountHost;

	private final String bankAccountPath;

	@Autowired
	public AppProperties(Environment environment) {
		this.earlyReconcileJobRunInterval = Integer
				.valueOf(environment.getRequiredProperty("pg.earlyReconcileJobRunInterval.mins"));
		this.saveTxnTopic = environment.getRequiredProperty("persister.save.pg.txns");
		this.updateTxnTopic = environment.getRequiredProperty("persister.update.pg.txns");
		this.updateTxnTopicAxis = environment.getRequiredProperty("persister.update.pg.txns.axis");
		this.saveTxnDumpTopic = environment.getRequiredProperty("persister.save.pg.txnsDump");
		this.updateTxnDumpTopic = environment.getRequiredProperty("persister.update.pg.txnsDump");
		this.updateTxnDumpTopicAxis = environment.getRequiredProperty("persister.update.pg.txnsDump.axis");
		this.idGenHost = environment.getRequiredProperty("egov.idgen.host");
		this.idGenPath = environment.getRequiredProperty("egov.idgen.path");
		this.idGenName = environment.getRequiredProperty("egov.idgen.ack.name");
		this.idGenFormat = environment.getRequiredProperty("egov.idgen.ack.format");
		this.collectionServiceHost = environment.getRequiredProperty("egov.collectionservice.host");
		this.collectionServiceCreatePath = environment.getRequiredProperty("egov.collectionservice.create.path");
		this.collectionServiceValidatePath = environment.getRequiredProperty("egov.collectionservice.validate.path");
		this.bankAccountHost = environment.getRequiredProperty("egov.bankaccountservice.host");
		this.bankAccountPath = environment.getRequiredProperty("egov.bankaccountservice.path");
		this.paymentCreatePath = environment.getRequiredProperty("egov.collectionservice.payment.create.path");
		this.paymentValidatePath = environment.getRequiredProperty("egov.collectionservice.payment.validate.path");
		this.idGenRefundFormat = environment.getRequiredProperty("egov.idgen.refund.format");
		this.saveRefundTxnTopic = environment.getRequiredProperty("persister.save.refund.pg.txns");
	}

}
