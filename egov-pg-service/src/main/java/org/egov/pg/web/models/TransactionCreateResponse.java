package org.egov.pg.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.pg.models.Transaction;

import java.util.Map;

import javax.validation.Valid;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class TransactionCreateResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("Transaction")
    @Valid
    private Transaction transaction;
    
	/*
	 * @JsonProperty("Other") private Map<String, String> other;
	 */
    
    @JsonProperty("Other")
    private Map<String, ?> other;
}
