package webdi.web;

import webdi.annotation.Controller;
import webdi.annotation.Route;
import webdi.exception.WebServerException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class RouteHandler {
    private final Method method;
    private final Object controller;
    private final Class<?> clazz;
    private final String contentType;

    public RouteHandler(Method method, Object controller, Class<?> clazz) {
        this.method = method;
        this.controller = controller;
        this.clazz = clazz;
        this.contentType = findContentType(method, clazz);
    }

    public Object execute(Object[] dependencies) {
        try {
            return method.invoke(controller, dependencies);
        } catch (Exception e) {
            throw new WebServerException("Couldn't invoke method " + method.getName() + " from controller " + clazz.getName(), e);
        }
    }

    public String getContentType() {
        return contentType;
    }

    public Parameter[] getParameters() {
        return method.getParameters();
    }

    private static String findContentType(Method method, Class<?> clazz) {
        Route route = method.getAnnotation(Route.class);
        if (route == null) {
            throw new WebServerException("Method " + method.getName() + " doesn't have @Route annotation");
        }
        if (route.contentType().equals("")) {
            Controller controllerAnnotation = clazz.getAnnotation(Controller.class);
            if (controllerAnnotation == null) {
                throw new WebServerException("Class " + clazz.getName() + " doesn't have @Controller annotation");
            }
            return clazz.getAnnotation(Controller.class).contentType();
        } else {
            return route.contentType();
        }
    }
}
