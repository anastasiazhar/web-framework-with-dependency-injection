package webdi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import webdi.ConvertingParameters;
import webdi.annotation.BodyParam;
import webdi.annotation.PathParam;
import webdi.annotation.QueryParam;
import webdi.exception.WebServerException;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public class MyWebServer implements Runnable{

    public static final String CONTENT_LENGTH_HEADER_NAME = "content-length";
    public static final String CONTENT_TYPE_HEADER_NAME = "content-type";
    public static final String CRLF = "\r\n";

    private final Socket socket;
    private final Router router;

    public MyWebServer(Socket socket, Router router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = socket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            OutputStream outputStream = socket.getOutputStream();
            String currentLine = null;
            RequestLine requestLine = null;
            HashMap<String, String> requestHeaders = new HashMap<>();
            RequestPart currentRequestPart = RequestPart.REQUEST_LINE;
            loop: while ((currentLine = bufferedReader.readLine()) != null) {
                switch (currentRequestPart) {
                    case REQUEST_LINE -> {
                        String[] split = currentLine.split(" ");
                        String method = split[0];
                        String path = split[1];
                        String protocol = split[2];
                        requestLine = new RequestLine(method, path, protocol);
                        currentRequestPart = RequestPart.REQUEST_HEADER;
                    }
                    case REQUEST_HEADER -> {
                        if (currentLine.isEmpty()) {
                            currentRequestPart = RequestPart.REQUEST_BODY;
                            break loop;
                        }
                        String[] split = currentLine.split(": ?");
                        String name = split[0].toLowerCase();
                        String value = split[1];
                        requestHeaders.put(name, value);
                    }
                }
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (requestHeaders.containsKey(CONTENT_LENGTH_HEADER_NAME) && requestHeaders.containsKey(CONTENT_TYPE_HEADER_NAME)) {
                int contentLength = Integer.parseInt(requestHeaders.get(CONTENT_LENGTH_HEADER_NAME));
                for (int i = 0; i < contentLength; i++) {
                    body.write((byte) bufferedReader.read());
                }
            }
            MyResponse response = handleRequest(new MyRequest(requestLine, requestHeaders, body));
            outputStream.write(response.statusLine().convert().getBytes(StandardCharsets.UTF_8));
            outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, String> entry : response.responseHeaders().entrySet()) {
                StringBuilder builder = new StringBuilder();
                builder.append(entry.getKey()).append(": ").append(entry.getValue());
                outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
            }
            outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
            if (response.responseBody() != null) {
                outputStream.write(response.responseBody().toByteArray());
            }
            outputStream.close();
        } catch (Exception e) {
            System.out.println("Exception in thread: " + Thread.currentThread().getName());
            e.printStackTrace();
        }

    }

    MyResponse handleRequest(MyRequest request) throws Exception {
        String requestPathParts[] = request.requestLine().path().split("\\?");
        Map<String, String> queryParameters = new HashMap<>();
        if (requestPathParts.length > 1) {
            String[] splitParameters = requestPathParts[1].split("&");
            for (String p : splitParameters) {
                String[] pair = p.split("=");
                queryParameters.put(pair[0], pair[1]);
            }
        }
        Optional<RoutedRequest> optionalRoutedRequest = router.route(request.requestLine());
        if (optionalRoutedRequest.isPresent()) {
            RoutedRequest routedRequest = optionalRoutedRequest.get();
            RouteHandler routeHandler = routedRequest.routeHandler();
            Map<String, String> pathParameters = routedRequest.pathParameters();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            StatusLine statusLine = new StatusLine("HTTP/1.1", 200, "OK");
            Object returnValue;
            List<Object> dependencies = new ArrayList<>();
            for (Parameter parameter : routeHandler.getParameters()) {
                if (parameter.getAnnotation(BodyParam.class) != null) {
                    if (parameter.getType() == ByteArrayOutputStream.class) {
                        dependencies.add(request.requestBody());
                    }
                    ObjectMapper mapper = new ObjectMapper();
                    Object argument = mapper.readValue(request.requestBody().toByteArray(), parameter.getType());
                    dependencies.add(argument);
                } else if (parameter.getAnnotation(PathParam.class) != null) {
                    String key = parameter.getAnnotation(PathParam.class).value();
                    if (!pathParameters.containsKey(key)) {
                        throw new WebServerException("No path parameter named " + key);
                    }
                    String value = pathParameters.get(key);
                    Class<?> parameterType = parameter.getType();
                    dependencies.add(ConvertingParameters.convertType(value, parameterType)
                            .orElseThrow(() -> new WebServerException("Failed to read path parameter " + value + " because it has unsupported type " + parameterType)));
                } else if (parameter.getAnnotation(QueryParam.class) != null) {
                    String key = parameter.getAnnotation(QueryParam.class).value();
                    if (!queryParameters.containsKey(key)) {
                        throw new WebServerException("No query parameter named " + key);
                    }
                    String value = queryParameters.get(key);
                    Class<?> parameterType = parameter.getType();
                    dependencies.add(ConvertingParameters.convertType(value, parameterType)
                            .orElseThrow(() -> new WebServerException("Failed to read query parameter " + value + " because it has unsupported type " + parameterType)));
                } else {
                    throw new WebServerException("Parameter has unsupported annotation");
                }
            }
            returnValue = routeHandler.execute(dependencies.toArray());
            if (returnValue instanceof ResponseEntity responseEntity) {
                if (responseEntity.getBody() == null) {
                    return new MyResponse(new StatusLine("HTTP/1.1", responseEntity.getStatus().get().code, responseEntity.getStatus().get().reason),
                            new HashMap<>(), body);
                }
                body = getBody(responseEntity.getBody(), routeHandler.getContentType());
                if (responseEntity.getStatus().isPresent()) {
                    Status status = responseEntity.getStatus().get();
                    statusLine = new StatusLine("HTTP/1.1", status.code, status.reason);
                }
            } else {
                String contentType = routeHandler.getContentType();
                body = getBody(returnValue, contentType);
            }
            HashMap<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE_HEADER_NAME, routeHandler.getContentType());
            headers.put(CONTENT_LENGTH_HEADER_NAME, "" + body.size());
            return new MyResponse(statusLine, headers, body);
        } else {
            StatusLine statusLine = new StatusLine("HTTP/1.1", 404, "not found");
            HashMap<String, String> headers = new HashMap<>();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write("<h1>404</h1>".getBytes());
            return new MyResponse(statusLine, headers, body);
        }
    }

    ByteArrayOutputStream getBody(Object returnValue, String contentType) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (returnValue instanceof String) {
            body.write(((String) returnValue).getBytes());
        } else if (contentType.equals("application/json")) {
            ObjectMapper mapper = new ObjectMapper();
            body.write(mapper.writeValueAsString(returnValue).getBytes());
        } else if (returnValue instanceof NamedFile) {
            body.write(((NamedFile) returnValue).bytes);
        } else {
            throw new WebServerException("Method annotated as @Route has unsupported return type");
        }
        return body;
    }
}

enum RequestPart {
    REQUEST_LINE,
    REQUEST_HEADER,
    REQUEST_BODY
}
