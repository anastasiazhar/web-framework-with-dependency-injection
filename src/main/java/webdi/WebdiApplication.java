package webdi;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import webdi.annotation.*;
import webdi.di.Injectable;
import webdi.di.InjectableBean;
import webdi.di.InjectableComponent;
import webdi.exception.InjectionException;
import webdi.web.HandlerKey;
import webdi.web.MyWebServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

public final class WebdiApplication {

    private WebdiApplication() {
    }

    public static void start(Class<?> c) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classloader.getResourceAsStream("config.properties");
        InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader);
        Map<String, String> configValues = new HashMap<>();
        try {
            for (String line; (line = reader.readLine()) != null;) {
                String[] splitLine = line.split("=");
                configValues.put(splitLine[0], splitLine[1]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Injectable> injectables = new ArrayList<>();
        AccessingAllClassesInPackage accessingAllClassesInPackage = new AccessingAllClassesInPackage();
        Set<Class<?>> classes = accessingAllClassesInPackage.findAllClassesUsingClassLoader(c.getPackageName());
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Component.class) || clazz.isAnnotationPresent(Controller.class)) {
                injectables.add(new InjectableComponent(clazz));
                continue;
            }
            if (clazz.isAnnotationPresent(Configuration.class)) {
                try {
                    Object configuration = clazz.getConstructor().newInstance();
                    for (Method method : clazz.getMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            injectables.add(new InjectableBean(method, configuration));
                        }
                    }
                } catch (Exception e) {
                    throw new InjectionException("Class " + clazz.getName() + " annotated as @Configuration doesn't have an empty constructor.");
                }
            }
        }
        Map<Class<?>, Injectable> classInjectableMap = new HashMap<>();
        Map<Injectable, Set<Class<?>>> dependencies = new HashMap<>();
        for (Injectable i : injectables) {
            classInjectableMap.put(i.getType(), i);
            Set<Class<?>> parameters = new HashSet<>();
            for (Parameter parameter : i.getParameters()) {
                if (!parameter.isAnnotationPresent(Value.class)) {
                    parameters.add(parameter.getType());
                }
            }
            dependencies.put(i, parameters);
        }
        DirectedAcyclicGraph<Injectable, DefaultEdge> dag = new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (Injectable i : injectables) {
            dag.addVertex(i);
        }
        for (Map.Entry<Injectable, Set<Class<?>>> dependency : dependencies.entrySet()) {
            Injectable key = dependency.getKey();
            Set<Class<?>> value = dependency.getValue();
            for (Class<?> clazz : value) {
                dag.addEdge(key, classInjectableMap.get(clazz));
            }
        }
        List<Injectable> orderedInjectables = new ArrayList<>(StreamSupport.stream(dag.spliterator(), false).toList());
        Collections.reverse(orderedInjectables);

        Map<Class<?>, Object> instances = new HashMap<>();
        for (Injectable injectable : orderedInjectables) {
            List<Object> arguments = new ArrayList<>();
            for (Parameter parameter : injectable.getParameters()) {
                Class<?> parameterType = parameter.getType();
                Value value = parameter.getAnnotation(Value.class);
                if (value == null) {
                    if (!instances.containsKey(parameterType)) {
                        throw new InjectionException("Failed to create " + injectable + ", because parameter " + parameterType.getName() +
                                " wasn't found in the list of crated instances.");
                    }
                    Object instance = instances.get(parameterType);
                    arguments.add(instance);
                    continue;
                }
                if (!parameterType.equals(String.class)) {
                    throw new InjectionException("Parameter can't be injected, because it's annotated as @Value, but isn't String.");
                }
                if (!configValues.containsKey(value.value())) {
                    throw new InjectionException("Parameter can't be injected, because the value can't be found in config.");
                }
                arguments.add(configValues.get(value.value()));
            }
            Object newInstance = injectable.construct(arguments.toArray());
            instances.put(injectable.getType(), newInstance);
        }
        for (Object object : instances.values()) {
            System.out.println(object.toString());
        }

        List<Object> controllers = new ArrayList<>();
        for (Map.Entry<Class<?>, Object> entry : instances.entrySet()) {
            if (entry.getKey().isAnnotationPresent(Controller.class)) {
                controllers.add(entry.getValue());
            }
        }

        HashMap<HandlerKey, Supplier<String>> routes = extractRoutes(controllers);

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            int counter = 1;
            while (true) {
                Socket socket = serverSocket.accept();
                MyWebServer webServer = new MyWebServer(socket, routes);
                Thread thread = new Thread(webServer, "[" + counter + "]");
                thread.start();
                counter += 1;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HashMap<HandlerKey, Supplier<String>> extractRoutes(List<Object> controllers) {
        HashMap<HandlerKey, Supplier<String>> map = new HashMap<>();
        for (Object controller : controllers) {
            Class<?> objectClass = controller.getClass();
            Method[] methods = objectClass.getMethods();
            for (Method method : methods) {
                Route route = method.getAnnotation(Route.class);
                if (route != null && method.getReturnType() == String.class) {
                    map.put(new HandlerKey(route.method(), route.value()), () -> {
                        try {
                            return (String)method.invoke(controller);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
        return map;
    }
}
