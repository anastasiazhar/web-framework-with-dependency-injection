package webdi.web;

import java.util.Optional;

public class ResponseEntity {

    Object body;
    Optional<Status> responseCode;

    public ResponseEntity(Object body, Optional<Status> responseCode) {
        this.body = body;
        this.responseCode = responseCode;
    }
}
