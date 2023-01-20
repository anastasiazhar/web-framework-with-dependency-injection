package webdi.di;

import webdi.exception.InjectionException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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
}
