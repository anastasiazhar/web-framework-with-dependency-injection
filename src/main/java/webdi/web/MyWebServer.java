package webdi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import webdi.exception.WebServerException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MyWebServer implements Runnable{

    public static final String CONTENT_LENGTH_HEADER_NAME = "content-length";
    public static final String CONTENT_TYPE_HEADER_NAME = "content-type";
    public static final String CRLF = "\r\n";

    private final Socket socket;
    private final HashMap<HandlerKey, RouteHandler> handlers;

    public MyWebServer(Socket socket, HashMap<HandlerKey, RouteHandler> handlers) {
        this.socket = socket;
        this.handlers = handlers;
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
        System.out.println("2 request = " + request);
        HandlerKey handlerKey = new HandlerKey(request.requestLine().method(), request.requestLine().path());
        if (handlers.containsKey(handlerKey)) {
            RouteHandler routeHandler = handlers.get(handlerKey);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            StatusLine statusLine = new StatusLine("HTTP/1.1", 200, "OK");;
            if (routeHandler.execute() instanceof ResponseEntity responseEntity) {
                // TODO: handle empty body
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
                Object returnValue = routeHandler.execute();
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
