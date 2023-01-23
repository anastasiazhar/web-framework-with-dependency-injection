package webdi.di;

import webdi.annotation.Inject;
import webdi.annotation.Named;
import webdi.exception.InjectionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Optional;

public class InjectableComponent implements Injectable {

    private final Class<?> clazz;
    private final Constructor<?> constructor;

    public InjectableComponent(Class<?> clazz) {
        this.clazz = clazz;
        Optional<Constructor<?>> constructorOptional = findConstructor(clazz);
        if (constructorOptional.isEmpty()) {
            throw new InjectionException("Couldn't find a suitable constructor for class " + clazz.getName());
        }
        this.constructor = constructorOptional.get();
    }

    @Override
    public Class<?> getType() {
        return clazz;
    }

    @Override
    public Parameter[] getParameters() {
        return constructor.getParameters();
    }

    @Override
    public Object construct(Object[] dependencies) {
        try {
            return constructor.newInstance(dependencies);
        } catch (Exception e) {
            throw new InjectionException("Failed to construct class " + clazz.getName(), e);
        }
    }

    @Override
    public Optional<String> getName() {
        Named named = clazz.getAnnotation(Named.class);
        if (named == null) {
            return Optional.empty();
        } else {
            return Optional.of(named.value());
        }
    }

    private static Optional<Constructor<?>> findConstructor(Class<?> c) {
        for (Constructor<?> constructor : c.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                return Optional.of(constructor);
            }
        }
        try {
            return Optional.of(c.getConstructor());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "InjectableComponent{" +
                "clazz=" + clazz.getName() +
                '}';
    }
}
