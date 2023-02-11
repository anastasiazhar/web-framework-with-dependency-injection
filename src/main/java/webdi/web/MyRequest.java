package webdi.web;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;

public record MyRequest(RequestLine requestLine, HashMap<String, List<String>> requestHeaders, ByteArrayOutputStream requestBody) {
}
