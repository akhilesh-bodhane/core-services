package org.egov.user.web.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.user.domain.model.enums.UserType;
import javax.validation.constraints.Pattern;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class
LoggedInUserUpdatePasswordRequest {
	@JsonProperty("RequestInfo")
	private RequestInfo requestInfo;
	    private String existingPassword;

	    
	 @Pattern(regexp = "^(?=.{8,}$)(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!])(?!.*\\s).*$",message = "Password must be at least 8 characters long, contain upper and lower case letters, a digit and a special character, and must not contain spaces")
    private String newPassword;
	private String tenantId;
	private UserType type;

	public org.egov.user.domain.model.LoggedInUserUpdatePasswordRequest toDomain() {
		return org.egov.user.domain.model.LoggedInUserUpdatePasswordRequest.builder()
				.existingPassword(existingPassword)
				.newPassword(newPassword)
				.userName(getUsername())
				.tenantId(tenantId)
                .type(type)
				.build();
	}

	private String getUsername() {
		return requestInfo == null || requestInfo.getUserInfo() == null ? null : requestInfo.getUserInfo().getUserName();
	}
}

