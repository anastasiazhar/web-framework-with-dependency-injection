package webdi.di;

import webdi.annotation.Named;
import webdi.annotation.Value;
import webdi.exception.InjectionException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

public class InjectableBean implements Injectable{

    private final Method method;
    private final Object configuration;

    public InjectableBean(Method method, Object configuration) {
        this.method = method;
        this.configuration = configuration;
    }

    @Override
    public Class<?> getType() {
        return method.getReturnType();
    }

    @Override
    public Parameter[] getParameters() {
        return method.getParameters();
    }

    @Override
    public Object construct(Object[] dependencies) {
        try {
            return method.invoke(configuration, dependencies);
        } catch (Exception e) {
            throw new InjectionException("Couldn't invoke method " + method.getName(), e);
        }
    }

    @Override
    public Optional<String> getName() {
        Named named = method.getAnnotation(Named.class);
        if (named == null) {
            return Optional.empty();
        } else {
            return Optional.of(named.value());
        }
    }

    @Override
    public String toString() {
        return "InjectableBean{" +
                "method=" + method.toGenericString() +
                ", class=" + configuration.getClass().getName() +
                ", configuration=" + configuration +
                '}';
    }
}
