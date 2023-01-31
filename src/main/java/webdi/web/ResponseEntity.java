package webdi.web;

import java.util.Map;
import java.util.Optional;

public class ResponseEntity {

    Object body;
    Optional<Status> responseCode;
    Map<String, String> headers;

    public ResponseEntity(Object body, Optional<Status> responseCode, Map<String, String> headers) {
        this.body = body;
        this.responseCode = responseCode;
        this.headers = headers;
    }
}
