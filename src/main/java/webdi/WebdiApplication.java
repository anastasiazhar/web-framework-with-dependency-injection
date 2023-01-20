package webdi;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.w3c.dom.ls.LSOutput;
import webdi.annotation.*;
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

        // ...
        AccessingAllClassesInPackage accessingAllClassesInPackage = new AccessingAllClassesInPackage();
        Set<Class<?>> classes = accessingAllClassesInPackage.findAllClassesUsingClassLoader(c.getPackageName());
        Map<Method, Set<Method>> beanDependencies = new HashMap<>();
        Map<Class<?>, Method> returnTypes = new HashMap<>();
        Map<Class<?>, Object> beanInstances = new HashMap<>();
        Map<Object, Set<Method>> configurations = new HashMap<>();
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Configuration.class)) {
                try {
                    Constructor<?> constructor = clazz.getConstructor();
                    Object configuration = constructor.newInstance();
                    configurations.put(configuration, new HashSet<>(List.of(clazz.getMethods())));
                } catch (Exception e) {
                    throw new InjectionException("Class " + clazz.getName() + " annotated as @Configuration doesn't have an empty constructor.");
                }
                for (Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(Bean.class)) {
                        if (!Object.class.isAssignableFrom(method.getReturnType())) {
                            throw new InjectionException("Method " + method.getName() + " annotated as @Bean doesn't return an Object.");
                        }
                        returnTypes.put(method.getReturnType(), method);
                    }
                }
            }
        }
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Configuration.class)) {
                for (Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(Bean.class)) {
                        Set<Method> set = new HashSet<>();
                        for (Parameter parameter : method.getParameters()) {
                            if (!parameter.isAnnotationPresent(Value.class)) {
                                if (returnTypes.get(parameter.getType()) != null) {
                                    set.add(returnTypes.get(parameter.getType()));
                                }
                            }
                        }
                        beanDependencies.put(method, set);
                    }
                }
            }
        }
        for (Map.Entry<Method, Set<Method>> d : beanDependencies.entrySet()) {
            System.out.println("method: " + d.getKey().getName());
            System.out.println("it's dependencies: ");
            for (Method m : d.getValue()) {
                System.out.print(m.getName() + "    ");
            }
            System.out.print("\n");
        }

        DirectedAcyclicGraph<Method, DefaultEdge> dag = new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (Method method : beanDependencies.keySet()) {
            dag.addVertex(method);
        }
        for (Map.Entry<Method, Set<Method>> entry : beanDependencies.entrySet()) {
            Method key = entry.getKey();
            Set<Method> value = entry.getValue();
            for (Method method : value) {
                dag.addEdge(key, method);
            }
        }
        List<Method> orderedBeanDependencies = new ArrayList<>(StreamSupport.stream(dag.spliterator(), false).toList());
        Collections.reverse(orderedBeanDependencies);
        System.out.println("\n\norder:");
        orderedBeanDependencies.forEach((method) -> {
            System.out.println(method.getName());
        });

        for (Method method : orderedBeanDependencies) {
            Object configuration = new Object();
            for (Map.Entry<Object, Set<Method>> entry : configurations.entrySet()) {
                if (entry.getValue().contains(method)) {
                    configuration = entry.getKey();
                }
            }
            List<Object> arguments = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                Class<?> parameterType = parameter.getType();
                Value value = parameter.getAnnotation(Value.class);
                if (value == null) {
                    if (!beanInstances.containsKey(parameterType)) {
                        throw new InjectionException();
                    }
                    Object instance = beanInstances.get(parameterType);
                    arguments.add(instance);
                    continue;
                }
                if (!parameterType.equals(String.class)) {
                    throw new InjectionException("Parameter can't be injected, because it's not String.");
                }
                if (!configValues.containsKey(value.value())) {
                    throw new InjectionException("Parameter can't be injected, because the respective entry can't be found in config.");
                }
                arguments.add(configValues.get(value.value()));
            }
            try {
                Object newInstance = method.invoke(configuration, arguments.toArray());
                beanInstances.put(method.getReturnType(), newInstance);
            } catch (Exception e) {
                throw new InjectionException(e);
            }
        }
        System.out.println("\n\ninstances:");
        for (Object object : beanInstances.values()) {
            System.out.println(object.toString());
        }
        // ...



        List<Class<?>> dependencies = findDependencies(c.getPackageName());
        Map<Class<?>, Object> instances = new HashMap<>();

        for (Class<?> aClass : dependencies) {
            try {
                Optional<Constructor<?>> optionalConstructor = receiveConstructor(aClass);
                if (optionalConstructor.isEmpty()) {
                    throw new InjectionException("Couldn't find a constructor for class: " + aClass.getName());
                }
                Constructor<?> constructor = optionalConstructor.get();
                List<Object> arguments = new ArrayList<>();
                for (Parameter parameter : constructor.getParameters()) {
                    Class<?> parameterType = parameter.getType();
                    Value value = parameter.getAnnotation(Value.class);
                    if (value == null) {
                        if (!instances.containsKey(parameterType)) {
                            instances.forEach((o1, o2) -> {
                                System.out.println(o1 + " -> " + o2);
                            });
                            throw new InjectionException("Failed to construct class " + aClass.getName() + " because dependency " + parameterType.getName() + " couldn't be found.");
                        }
                        Object instance = instances.get(parameterType);
                        arguments.add(instance);
                        continue;
                    }
                    if (!parameterType.equals(String.class)) {
                        throw new InjectionException("Parameter can't be injected, because it's not String.");
                    }
                    if (!configValues.containsKey(value.value())) {
                        throw new InjectionException("Parameter can't be injected, because the respective entry can't be found in config.");
                    }
                    arguments.add(configValues.get(value.value()));
                }
                Object newInstance = constructor.newInstance(arguments.toArray());
                instances.put(aClass, newInstance);
            } catch (Exception e) {
                throw new InjectionException(e);
            }
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

    private static List<Class<?>> findDependencies(String packageName) {
        AccessingAllClassesInPackage accessingAllClassesInPackage = new AccessingAllClassesInPackage();
        Set<Class<?>> classes = accessingAllClassesInPackage.findAllClassesUsingClassLoader(packageName);

        Map<Class<?>, Set<Class<?>>> dependencies = new HashMap<>();
        for (Class<?> aClass : classes) {
            if (aClass.isAnnotationPresent(Component.class) || aClass.isAnnotationPresent(Controller.class)) {
                Set<Class<?>> parameters = new HashSet<>();
                Optional<Constructor<?>> optionalConstructor = receiveConstructor(aClass);
                if (optionalConstructor.isEmpty()) {
                    throw new InjectionException("Couldn't find a constructor for class: " + aClass.getName());
                }
                Constructor<?> constructor = optionalConstructor.get();
                for (Parameter parameter : constructor.getParameters()) {
                    if (!parameter.isAnnotationPresent(Value.class)) {
                        parameters.add(parameter.getType());
                    }
                }
                dependencies.put(aClass, parameters);
            }

        }

        DirectedAcyclicGraph<Class<?>, DefaultEdge> dag = new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (Class<?> clazz : dependencies.keySet()) {
            dag.addVertex(clazz);
        }
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : dependencies.entrySet()) {
            Class<?> key = entry.getKey();
            Set<Class<?>> value = entry.getValue();
            for (Class<?> clazz : value) {
                dag.addEdge(key, clazz);
            }
        }

        List<Class<?>> orderedDependencies = new ArrayList<>(StreamSupport.stream(dag.spliterator(), false).toList());
        Collections.reverse(orderedDependencies);
        return orderedDependencies;
    }

    private static Optional<Constructor<?>> receiveConstructor(Class<?> c) {
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
