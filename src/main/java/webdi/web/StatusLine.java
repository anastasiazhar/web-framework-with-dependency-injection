package webdi.web;

public record StatusLine(String protocol, int statusCode, String reason) {
    String convert() {
        StringBuilder builder = new StringBuilder();
        builder.append(protocol).append(" ").append(statusCode);
        if (reason != null) {
            builder.append(" ").append(reason);
        }

        return builder.toString();
    }
}
