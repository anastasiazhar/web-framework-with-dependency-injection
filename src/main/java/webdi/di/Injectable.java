package webdi.di;

import java.lang.reflect.Parameter;
import java.util.Optional;

public interface Injectable {

    Class<?> getType();
    Parameter[] getParameters();
    Object construct(Object[] dependencies);
    Optional<String> getName();
}
