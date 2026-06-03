package com.simon.campus.service.ingest;

final class IngestErrorMessage {

    static final int MAX_ERROR_MSG_LENGTH = 500;

    private IngestErrorMessage() {
    }

    static String from(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_MSG_LENGTH
            ? message.substring(0, MAX_ERROR_MSG_LENGTH)
            : message;
    }
}
