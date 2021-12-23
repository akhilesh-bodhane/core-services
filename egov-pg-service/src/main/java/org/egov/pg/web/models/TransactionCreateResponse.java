package org.egov.pg.web.models;

import java.util.Map;

import javax.validation.Valid;

import org.egov.pg.models.Transaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionCreateResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo;

    @JsonProperty("Transaction")
    @Valid
    private Transaction transaction;
    
    @JsonProperty("Other")
    private Map<String, String> other;

}
