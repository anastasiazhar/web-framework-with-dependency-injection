package webdi.web;

import java.util.Map;

public record RoutedRequest(RouteHandler routeHandler, Map<String, String> pathParameters) {
}
