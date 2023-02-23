package webdi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import webdi.ConvertingParameters;
import webdi.annotation.*;
import webdi.exception.WebServerException;

import java.io.*;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MyWebServer implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(MyWebServer.class);


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
            HashMap<String, List<String>> requestHeaders = new HashMap<>();
            RequestPart currentRequestPart = RequestPart.REQUEST_LINE;
            loop: while ((currentLine = bufferedReader.readLine()) != null) {
                switch (currentRequestPart) {
                    case REQUEST_LINE -> {
                        String[] split = currentLine.split(" ");
                        String method = split[0];
                        String path = split[1];
                        String protocol = split[2];
                        requestLine = new RequestLine(method, path, protocol);
                        logger.info("Serving request " + method + " " + path + " using protocol " + protocol);
                        currentRequestPart = RequestPart.REQUEST_HEADER;
                    }
                    case REQUEST_HEADER -> {
                        if (currentLine.isEmpty()) {
                            currentRequestPart = RequestPart.REQUEST_BODY;
                            break loop;
                        }
                        int index = currentLine.indexOf(":");
                        String name = currentLine.substring(0, index).trim().toLowerCase();
                        String value = currentLine.substring(index + 1).trim();
                        if (requestHeaders.containsKey(name)) {
                            requestHeaders.get(name).add(value);
                        } else {
                            requestHeaders.put(name, List.of(value));
                        }
                    }
                }
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (requestHeaders.containsKey(CONTENT_LENGTH_HEADER_NAME) && requestHeaders.containsKey(CONTENT_TYPE_HEADER_NAME)) {
                int contentLength = Integer.parseInt(requestHeaders.get(CONTENT_LENGTH_HEADER_NAME).get(0));
                for (int i = 0; i < contentLength; i++) {
                    body.write((byte) bufferedReader.read());
                }
            }
            MyResponse response = handleRequest(new MyRequest(requestLine, requestHeaders, body));
            outputStream.write(response.statusLine().convert().getBytes(StandardCharsets.UTF_8));
            outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, List<String>> entry : response.responseHeaders().entrySet()) {
                StringBuilder builder = new StringBuilder();
                String key = entry.getKey();
                List<String> values = entry.getValue();
                for (String value : values) {
                    builder.append(key).append(": ").append(value);
                    outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
                    builder.setLength(0);
                }
            }
            outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
            if (response.responseBody() != null) {
                outputStream.write(response.responseBody().toByteArray());
            }
            outputStream.close();
            logger.info("Request served");
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
        Map<String, String> cookies = new HashMap<>();
        if (request.requestHeaders().containsKey("cookie")) {
            for (String allCookies : request.requestHeaders().get("cookie")) {
                String[] splitCookies = allCookies.split(";");
                for (String splitCookie : splitCookies) {
                    String[] cookieProperties = splitCookie.split("=");
                    cookies.put(cookieProperties[0].trim(), cookieProperties[1].trim());
                }
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
                } else if (parameter.getAnnotation(Header.class) != null) {
                    String key = parameter.getAnnotation(Header.class).value().toLowerCase();
                    if (!request.requestHeaders().containsKey(key)) {
                        throw new WebServerException("No header named " + key);
                    }
                    dependencies.add(request.requestHeaders().get(key).get(0));
                } else if (parameter.getAnnotation(Cookie.class) != null) {
                    String cookieName = parameter.getAnnotation(Cookie.class).value().toLowerCase();
                    dependencies.add(cookies.get(cookieName));
                } else {
                    throw new WebServerException("Parameter has unsupported annotation");
                }
            }
            returnValue = routeHandler.execute(dependencies.toArray());
            HashMap<String, List<String>> headers = new HashMap<>();
            if (returnValue instanceof ResponseEntity responseEntity) {
                headers.putAll(responseEntity.getHeaders());
                body = getBody(responseEntity.getBody(), routeHandler.getContentType());
                if (responseEntity.getStatus().isPresent()) {
                    Status status = responseEntity.getStatus().get();
                    statusLine = new StatusLine("HTTP/1.1", status.code, status.reason);
                }
                for (ResponseCookie cookie : responseEntity.getCookies()) {
                    String cookieLine = cookie.name() + "=" + cookie.value() + ";" +
                            "path=" + cookie.path() + ";" +
                            "expiration=" + cookie.expiration();
                    if (headers.containsKey("set-cookie")) {
                        headers.get("set-cookie").add(cookieLine);
                    } else {
                        List<String> list = new ArrayList<>();
                        list.add(cookieLine);
                        headers.put("set-cookie", list);
                    }
                }
            } else {
                String contentType = routeHandler.getContentType();
                body = getBody(returnValue, contentType);
            }
            headers.put(CONTENT_TYPE_HEADER_NAME, List.of(routeHandler.getContentType()));
            headers.put(CONTENT_LENGTH_HEADER_NAME, List.of(Integer.toString(body.size())));
            return new MyResponse(statusLine, headers, body);
        } else {
            logger.info("Couldn't find appropriate handler, serving 404");
            StatusLine statusLine = new StatusLine("HTTP/1.1", 404, "not found");
            HashMap<String, List<String>> headers = new HashMap<>();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write("<h1>404</h1>".getBytes());
            return new MyResponse(statusLine, headers, body);
        }
    }

    ByteArrayOutputStream getBody(Object returnValue, String contentType) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (returnValue == null) {
            return body;
        }
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
