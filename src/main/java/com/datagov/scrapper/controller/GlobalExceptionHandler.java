package com.datagov.scrapper.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Suppress the stack trace for AsyncRequestTimeoutException which happens
     * frequently when SSE clients disconnect or timeout. It is completely normal.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        logger.debug("SSE Client disconnected or async request timed out.");
    }

    /**
     * Suppress stack traces when browser/client abruptly disconnects or aborts the TCP connection.
     */
    @ExceptionHandler(java.io.IOException.class)
    public void handleIOException(java.io.IOException e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("aborted") || msg.contains("Broken pipe") || msg.contains("connection") || msg.contains("reset"))) {
            logger.debug("Client closed connection: {}", msg);
        } else {
            logger.warn("I/O exception occurred: {}", msg);
        }
    }
}
