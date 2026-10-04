package com.esg.notifications;

/** Outcome of handing one message to an external channel. */
public record SendResult(Status status, String detail) {

    public enum Status { SENT, NOT_CONFIGURED, FAILED }

    public static SendResult sent() { return new SendResult(Status.SENT, null); }
    public static SendResult notConfigured(String why) { return new SendResult(Status.NOT_CONFIGURED, why); }
    public static SendResult failed(String why) { return new SendResult(Status.FAILED, why); }
}
