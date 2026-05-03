package com.buyology.ecommerce.game.dto;

public class AlreadyPlayedException extends RuntimeException {
    public AlreadyPlayedException(String message) {
        super(message);
    }
}
