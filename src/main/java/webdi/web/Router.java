package webdi.web;

import java.util.Optional;

public interface Router {

    Optional<RoutedRequest> route(RequestLine requestLine);
}
