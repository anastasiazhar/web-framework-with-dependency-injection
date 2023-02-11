package webdi.web;

public record ResponseCookie(String name, String value, String path, long expiration) {
}
