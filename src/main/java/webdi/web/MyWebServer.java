package webdi.web;

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
    private final HashMap<HandlerKey, Supplier<String>> handlers;

    public MyWebServer(Socket socket, HashMap<HandlerKey, Supplier<String>> handlers) {
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
            if (requestHeaders.containsKey(CONTENT_LENGTH_HEADER_NAME) || requestHeaders.containsKey(CONTENT_TYPE_HEADER_NAME)) {
                int current;
                while ((current = bufferedReader.read()) != -1) {
                    body.write((byte) current);
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
        HandlerKey handlerKey = new HandlerKey(request.requestLine().method(), request.requestLine().path());
        if (handlers.containsKey(handlerKey)) {
            Supplier<String> supplier = handlers.get(handlerKey);
            String html = supplier.get();
            StatusLine statusLine = new StatusLine("HTTP/1.1", 200, "OK");
            HashMap<String, String> headers = new HashMap<>();
            headers.put(CONTENT_TYPE_HEADER_NAME, "text/html");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(html.getBytes(StandardCharsets.UTF_8));
            headers.put(CONTENT_LENGTH_HEADER_NAME, "" + body.size());
            return new MyResponse(statusLine, headers, body);
        } else {
            StatusLine statusLine = new StatusLine("HTTP/1.1", 404, "not found (you dolboyeb)");
            HashMap<String, String> headers = new HashMap<>();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write("<h1>404</h1>".getBytes());
            return new MyResponse(statusLine, headers, body);
        }


//        System.out.println("requestLine = " + request.requestLine());
//        System.out.println("HEADERS START");
//        request.requestHeaders().entrySet().forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
//        System.out.println("HEADERS END");
//        System.out.println("body size = " + request.requestBody().size());
//        System.out.println("body as string = " + request.requestBody());
//
//        if (request.requestLine().path().equals("/home") && request.requestLine().method().equals("GET")) {
//            StatusLine statusLine = new StatusLine("HTTP/1.1", 200, "OK");
//            HashMap<String, String> headers = new HashMap<>();
//            headers.put(CONTENT_TYPE_HEADER_NAME, "text/html");
//            ByteArrayOutputStream body = new ByteArrayOutputStream();
//            body.write("<h1>you suck.</h1>".getBytes(StandardCharsets.UTF_8));
//            headers.put(CONTENT_LENGTH_HEADER_NAME, "" + body.size());
//
//            return new MyResponse(statusLine, headers, body);
//        } else {
//            StatusLine statusLine = new StatusLine("HTTP/1.1", 404, "not found (you dolboyeb)");
//            HashMap<String, String> headers = new HashMap<>();
//            ByteArrayOutputStream body = new ByteArrayOutputStream();
//
//            return new MyResponse(statusLine, headers, body);
//        }

    }
}

enum RequestPart {
    REQUEST_LINE,
    REQUEST_HEADER,
    REQUEST_BODY
}
