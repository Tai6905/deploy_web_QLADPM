package com.modernshopflow.catalog.domain;

public class CatalogConflictException extends RuntimeException {

    public CatalogConflictException(String message) {
        super(message);
    }
}
