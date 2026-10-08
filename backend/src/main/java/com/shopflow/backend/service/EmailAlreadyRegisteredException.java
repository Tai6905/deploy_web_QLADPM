package com.shopflow.backend.service;

/** Signals the expected conflict when a registration email is already in use. */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Email is already registered");
    }
}