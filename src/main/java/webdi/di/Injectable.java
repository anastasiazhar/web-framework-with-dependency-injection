package webdi.di;

import java.lang.reflect.Parameter;

public interface Injectable {

    Class<?> getType();
    Parameter[] getParameters();
    Object construct(Object[] dependencies);
}
