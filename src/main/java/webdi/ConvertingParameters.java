package webdi;

import webdi.exception.WebServerException;

import java.util.Optional;

public final class ConvertingParameters {

    private ConvertingParameters() {
    }

    public static Optional<Object> convertType(String value, Class<?> parameterType) {
        if (parameterType == String.class) {
            return Optional.of(value);
        } else if (parameterType == Integer.class || parameterType == int.class) {
            return Optional.of(Integer.valueOf(value));
        } else if (parameterType == Double.class || parameterType == double.class) {
            return Optional.of(Double.valueOf(value));
        } else if (parameterType == Boolean.class || parameterType == boolean.class) {
            return Optional.of(Boolean.valueOf(value));
        } else {
            return Optional.empty();
        }
    }
}
