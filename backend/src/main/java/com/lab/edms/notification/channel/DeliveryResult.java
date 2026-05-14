package com.lab.edms.notification.channel;

public record DeliveryResult(boolean success, String errorMessage) {

    public static DeliveryResult ok() {
        return new DeliveryResult(true, null);
    }

    public static DeliveryResult fail(String error) {
        return new DeliveryResult(false, error);
    }
}
