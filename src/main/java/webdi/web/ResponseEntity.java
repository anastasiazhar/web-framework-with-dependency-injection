package webdi.web;

import java.util.*;

public class ResponseEntity {

    private final Object body;
    private final Optional<Status> status;
    private final Map<String, String> headers;
    private final Set<ResponseCookie> cookies;


    public static ResponseEntity of(Object body) {
        return new Builder().body(body).build();
    }

    public static ResponseEntity of(Object body, Status status) {
        return new Builder().body(body).status(status).build();
    }

    public static ResponseEntity ok(Object body) {
        return new Builder().body(body).status(Status.OK).build();
    }

    public static ResponseEntity noContent() {
        return new Builder().status(Status.NO_CONTENT).build();
    }

    public static ResponseEntity badRequest(Object body) {
        return new Builder().body(body).status(Status.BAD_REQUEST).build();
    }

    public static ResponseEntity unauthorizedError(Object body) {
        return new Builder().body(body).status(Status.UNAUTHORIZED_ERROR).build();
    }

    public static ResponseEntity forbidden(Object body) {
        return new Builder().body(body).status(Status.FORBIDDEN).build();
    }

    public static ResponseEntity notFound(Object body) {
        return new Builder().body(body).status(Status.NOT_FOUND).build();
    }

    public static ResponseEntity internalServerError(Object body) {
        return new Builder().body(body).status(Status.INTERNAL_SERVER_ERROR).build();
    }

    public static class Builder {
        private Object body;
        private Optional<Status> status = Optional.empty();
        private Map<String, String> headers = new HashMap<>();
        private Set<ResponseCookie> cookies = new HashSet<>();


        public static Builder newInstance() {
            return new Builder();
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder status(Status status) {
            this.status = Optional.of(status);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder cookies(Set<ResponseCookie> cookies) {
            this.cookies = cookies;
            return this;
        }

        public Builder cookie(ResponseCookie cookie) {
            this.cookies.add(cookie);
            return this;
        }

        public ResponseEntity build() {
            return new ResponseEntity(this);
        }
    }

    public ResponseEntity(Object body, Optional<Status> status, Map<String, String> headers, Set<ResponseCookie> cookies) {
        this.body = body;
        this.status = status;
        this.headers = headers;
        this.cookies = cookies;
    }

    public ResponseEntity(Builder builder) {
        this.body = builder.body;
        this.status = builder.status;
        this.headers = builder.headers;
        this.cookies = builder.cookies;
    }

    public Object getBody() {
        return body;
    }

    public Optional<Status> getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Set<ResponseCookie> getCookies() {return cookies; }
}
