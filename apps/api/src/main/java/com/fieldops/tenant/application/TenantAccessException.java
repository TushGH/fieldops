package com.fieldops.tenant.application;

public class TenantAccessException extends RuntimeException {
    private final int status;
    private final String code;

    public TenantAccessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static TenantAccessException forbidden() {
        return new TenantAccessException(403, "TENANT_ACCESS_DENIED", "Access to this tenant operation is denied.");
    }

    public static TenantAccessException notFound() {
        return new TenantAccessException(404, "RESOURCE_NOT_FOUND", "Resource not found in the selected tenant.");
    }
}
