package com.shandong.policyagent.entity;

public enum UrlImportJobStatus {
    PENDING,
    CRAWLING,
    PROCESSING,
    WAITING_CONFIRM,
    PARTIALLY_IMPORTED,
    COMPLETED,
    CANCELED,
    FAILED
}