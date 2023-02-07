package webdi.web;

import webdi.exception.WebServerException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TrieRouter implements Router {
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("^\\{([a-z]+)(:(.+))?\\}$");
    private final Map<String, TrieNode> methodTrie = new HashMap<>();

    public TrieRouter(Map<HandlerKey, RouteHandler> routesMap) {
        for (Map.Entry<HandlerKey, RouteHandler> entry : routesMap.entrySet()) {
            HandlerKey key = entry.getKey();
            String method = key.method();
            String path = key.path();
            RouteHandler handler = entry.getValue();
            String[] pathParts = path.split("/");
            if (!methodTrie.containsKey(method)) {
                methodTrie.put(method, new TrieNode());
            }
            TrieNode node = methodTrie.get(method);
            for (String part : pathParts) {
                if (!part.isEmpty()) {
                    Matcher matcher = PATH_PARAMETER_PATTERN.matcher(part);
                    TrieNode nextNode;
                    if (matcher.matches()) {
                        String parameterName = matcher.group(1);
                        Optional<String> parameterRegex = Optional.ofNullable(matcher.group(3));
                        PathParameterDefinition pathParameterDefinition = new PathParameterDefinition(parameterName, parameterRegex);
                        if (!node.variableChildren.containsKey(pathParameterDefinition)) {
                            nextNode = new TrieNode();
                            node.variableChildren.put(pathParameterDefinition, nextNode);
                        } else {
                            nextNode = node.variableChildren.get(pathParameterDefinition);
                        }
                    } else {
                        if (!node.constantChildren.containsKey(part)) {
                            nextNode = new TrieNode();
                            node.constantChildren.put(part, nextNode);
                        } else {
                            nextNode = node.constantChildren.get(part);
                        }
                    }
                    node = nextNode;
                }
            }
            node.handler = Optional.of(handler);
        }
    }

    @Override
    public Optional<RoutedRequest> route(RequestLine requestLine) {
        String path = requestLine.path();
        String[] pathParts = path.split("/");
        String method = requestLine.method();
        if (!methodTrie.containsKey(method)) {
            return Optional.empty();
        }
        TrieNode node = methodTrie.get(method);
        Map<String, String> pathParametersMap = new HashMap<>();
        for (String part : pathParts) {
            if (!part.isEmpty()) {
                if (node.constantChildren.containsKey(part)){
                    node = node.constantChildren.get(part);
                } else {
                    boolean matchFound = false;
                    for (Map.Entry<PathParameterDefinition, TrieNode> entry : node.variableChildren.entrySet()) {
                        PathParameterDefinition pathParameterDefinition = entry.getKey();
                        String name = pathParameterDefinition.name;
                        Optional<String> optionalPattern = pathParameterDefinition.pattern;
                        if (optionalPattern.isEmpty() || part.matches(optionalPattern.get())) {
                            pathParametersMap.put(name, part);
                            node = node.variableChildren.get(entry.getKey());
                            matchFound = true;
                            break;
                        }
                    }
                    if (!matchFound) {
                        return Optional.empty();
                    }
                }
            }
        }
        return node.handler.map(routeHandler -> new RoutedRequest(routeHandler, pathParametersMap));
    }

    private static class TrieNode {
        private Optional<RouteHandler> handler;
        private final Map<String, TrieNode> constantChildren;
        private final Map<PathParameterDefinition, TrieNode> variableChildren;

        public TrieNode(Optional<RouteHandler> handler, Map<String, TrieNode> constantChildren, Map<PathParameterDefinition, TrieNode> variableChildren) {
            this.handler = handler;
            this.constantChildren = constantChildren;
            this.variableChildren = variableChildren;
        }

        public TrieNode() {
            this.handler = Optional.empty();
            this.constantChildren = new HashMap<>();
            this.variableChildren = new HashMap<>();
        }

        @Override
        public String toString() {
            var children = constantChildren.entrySet().stream().map(entry -> entry.getKey() + " -> " + entry.getValue() + ", ").collect(Collectors.joining());
            return "terminal = " + handler.isPresent() + " children = [" + children + "]";
        }
    }

    private record PathParameterDefinition(String name, Optional<String> pattern) {}
}
