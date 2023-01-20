package webdi.web;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public record MyRequest(RequestLine requestLine, HashMap<String, String> requestHeaders, ByteArrayOutputStream requestBody) {
}
