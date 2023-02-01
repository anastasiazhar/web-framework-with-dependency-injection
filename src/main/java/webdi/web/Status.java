package webdi.web;

public enum Status {
    OK(200, "OK"),
    NO_CONTENT(204, "NO CONTENT"),
    PERMANENT_REDIRECT(301, "PERMANENT REDIRECT"),
    TEMPORARY_REDIRECT(302, "TEMPORARY REDIRECT"),
    NOT_MODIFIED(304, "NOT MODIFIED"),
    BAD_REQUEST(400, "BAD REQUEST"),
    UNAUTHORIZED_ERROR(401, "UNAUTHORIZED ERROR"),
    FORBIDDEN(403, "FORBIDDEN"),
    NOT_FOUND(404, "NOT FOUND"),
    INTERNAL_SERVER_ERROR(500, "INTERNAL SERVER ERROR"),
    NOT_IMPLEMENTED(501, "NOT IMPLEMENTED")
    ;
    int code;
    String reason;

    Status(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }
}
