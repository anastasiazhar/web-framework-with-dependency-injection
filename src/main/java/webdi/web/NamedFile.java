package webdi.web;

import webdi.exception.WebServerException;

import java.io.InputStream;

public class NamedFile {

    String name;
    byte[] bytes;

    public NamedFile(String name) {
        this.name = name;
        this.bytes = getBytes(name);
    }

    private static byte[] getBytes(String name) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream(name)) {
            if (is == null) {
                throw new WebServerException("");
            }
            return is.readAllBytes();
        } catch (Exception e) {
            throw new WebServerException(e);
        }
    }
}
