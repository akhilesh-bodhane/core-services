package org.egov.user.web.controller;

import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        ErrorResponse response = new ErrorResponse();
        ResponseInfo responseInfo = new ResponseInfo();
        responseInfo.setStatus(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        response.setResponseInfo(responseInfo);

        FieldError fieldError = ex.getBindingResult().getFieldError();
        Error error = new Error();
        error.setCode(HttpStatus.BAD_REQUEST.value());
        if (fieldError != null) {
            error.setDescription(fieldError.getDefaultMessage());
        } else {
            error.setDescription("Validation failed");
        }
        response.setError(error);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse();
        ResponseInfo responseInfo = new ResponseInfo();
        responseInfo.setStatus(String.valueOf(HttpStatus.BAD_REQUEST.value()));
        response.setResponseInfo(responseInfo);

        Error error = new Error();
        error.setCode(HttpStatus.BAD_REQUEST.value());
        error.setDescription(ex.getMessage() == null ? "Bad request" : ex.getMessage());
        response.setError(error);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
