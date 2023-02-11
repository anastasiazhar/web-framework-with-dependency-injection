package webdi.web;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;

public record MyResponse(StatusLine statusLine, HashMap<String, List<String>> responseHeaders, ByteArrayOutputStream responseBody) {
}
