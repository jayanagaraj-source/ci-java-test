package com.example.taskapi.common.error;

import java.io.IOException;

/** Thrown while reading a request body that exceeds the configured size limit. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException() {
        super("Request body exceeds the configured limit");
    }
}
