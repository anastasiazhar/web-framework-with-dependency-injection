package webdi.di;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;

public interface Injectable {

    List<Class<?>> getImplementedTypes();

    Parameter[] getParameters();
    Object construct(Object[] dependencies);
    Optional<String> getName();
}
