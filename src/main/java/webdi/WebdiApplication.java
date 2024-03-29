package webdi;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import webdi.annotation.*;
import webdi.di.Injectable;
import webdi.di.InjectableBean;
import webdi.di.InjectableComponent;
import webdi.di.NamedClass;
import webdi.exception.InjectionException;
import webdi.web.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.StreamSupport;

public final class WebdiApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebdiApplication.class);

    private WebdiApplication() {
    }

    public static void start() {
        try {
            // https://stackoverflow.com/a/34948763
            String className = Thread.currentThread().getStackTrace()[2].getClassName();
            logger.debug("Identified main class as: " + className);
            Class<?> clazz = Class.forName(className);
            start(clazz);
        } catch (ClassNotFoundException e) {
            logger.error("Couldn't find main class", e);
        }
    }

    public static void start(Class<?> c) {
        logger.info("Starting webdi application for class {}", c.getName());
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classloader.getResourceAsStream("config.properties");
        InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader);
        Map<String, String> configValues = new HashMap<>();
        try {
            for (String line; (line = reader.readLine()) != null;) {
                String[] splitLine = line.split("=");
                String name = splitLine[0];
                String value = splitLine[1];
                configValues.put(name, value);
            }
        } catch (Exception e) {
            logger.error("failed to read config", e);
            throw new RuntimeException(e);
        }

        List<Injectable> injectables = new ArrayList<>();
        Set<Class<?>> classes = AccessingAllClassesInPackage.findAllClassesUsingClassLoader(c.getPackageName());
        logger.info("Scanning started");
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Component.class)) {
                logger.info("Found component " + clazz.getName());
                injectables.add(new InjectableComponent(clazz));
            } else if (clazz.isAnnotationPresent(Controller.class)) {
                logger.info("Found controller " + clazz.getName());
                injectables.add(new InjectableComponent(clazz));
            } else if (clazz.isAnnotationPresent(Configuration.class)) {
                logger.info("Found configuration " + clazz.getName());
                try {
                    Object configuration = clazz.getConstructor().newInstance();
                    for (Method method : clazz.getMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            logger.info("Found bean " + method.getName());
                            injectables.add(new InjectableBean(method, configuration));
                        }
                    }
                } catch (Exception e) {
                    throw new InjectionException("Class " + clazz.getName() + " annotated as @Configuration doesn't have an empty constructor.");
                }
            }
        }
        logger.info("Scanning finished");
        Map<NamedClass, Injectable> classInjectableMap = new HashMap<>();
        Map<Injectable, Set<NamedClass>> dependencies = new HashMap<>();
        for (Injectable i : injectables) {
            String name = i.getName().orElse(null);
            for (Class<?> clazz : i.getImplementedTypes()) {
                classInjectableMap.put(new NamedClass(name, clazz), i);
            }
            Set<NamedClass> parameters = new HashSet<>();
            for (Parameter parameter : i.getParameters()) {
                if (!parameter.isAnnotationPresent(Value.class)) {
                    Named n = parameter.getAnnotation(Named.class);
                    if (n != null) {
                        parameters.add(new NamedClass(n.value(), parameter.getType()));
                    } else {
                        parameters.add(new NamedClass(null, parameter.getType()));
                    }
                }
            }
            dependencies.put(i, parameters);
        }
        DirectedAcyclicGraph<Injectable, DefaultEdge> dag = new DirectedAcyclicGraph<>(DefaultEdge.class);
        for (Injectable i : injectables) {
            dag.addVertex(i);
        }
        for (Map.Entry<Injectable, Set<NamedClass>> dependency : dependencies.entrySet()) {
            Injectable key = dependency.getKey();
            Set<NamedClass> value = dependency.getValue();
            for (NamedClass namedClass : value) {
                dag.addEdge(key, classInjectableMap.get(namedClass));
            }
        }
        List<Injectable> orderedInjectables = new ArrayList<>(StreamSupport.stream(dag.spliterator(), false).toList());
        Collections.reverse(orderedInjectables);

        Map<Class<?>, Map<String, Object>> instances = new HashMap<>();
        logger.info("Starting to create dependencies");
        for (Injectable injectable : orderedInjectables) {
            logger.info("Creating dependency " + injectable);
            List<Object> arguments = new ArrayList<>();
            for (Parameter parameter : injectable.getParameters()) {
                Class<?> parameterType = parameter.getType();
                Value value = parameter.getAnnotation(Value.class);
                if (value == null) {
                    if (!instances.containsKey(parameterType)) {
                        throw new InjectionException("Failed to create " + injectable + ", because parameter " + parameterType.getName() +
                                " wasn't found in the list of created instances.");
                    }
                    Map<String, Object> namesMap = instances.get(parameterType);
                    Named named = parameter.getAnnotation(Named.class);
                    if (named != null) {
                        arguments.add(namesMap.get(named.value()));
                    } else {
                        arguments.add(namesMap.get(null));
                    }
                    continue;
                }
                if (!configValues.containsKey(value.value())) {
                    throw new InjectionException("Parameter can't be injected, because the value can't be found in config.");
                }
                String parameterValue = configValues.get(value.value());
                arguments.add(ConvertingParameters.convertType(parameterValue, parameterType).orElseThrow(() -> new InjectionException("Failed to inject value " +
                        parameterValue + " from config, because it has unsupported type " + parameterType.getName())));
            }
            Object newInstance = injectable.construct(arguments.toArray());
            for (Class<?> clazz : injectable.getImplementedTypes()) {
                if (!instances.containsKey(clazz)) {
                    instances.put(clazz, new HashMap<>());
                }
                Map<String, Object> namesMap = instances.get(clazz);
                namesMap.put(injectable.getName().orElse(null), newInstance);
            }
        }

        List<Object> controllers = new ArrayList<>();
        for (Map.Entry<Class<?>, Map<String, Object>> entry : instances.entrySet()) {
            if (entry.getKey().isAnnotationPresent(Controller.class)) {
                controllers.addAll(entry.getValue().values());
            }
        }

        logger.info("Started scanning routes");
        HashMap<HandlerKey, RouteHandler> routes = extractRoutes(controllers);
        Router router = new TrieRouter(routes);
        logger.info("Finished scanning routes and created router");

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            int counter = 1;
            while (true) {
                Socket socket = serverSocket.accept();
                logger.info("Received new connection from " + socket.getRemoteSocketAddress());
                MyWebServer webServer = new MyWebServer(socket, router);
                Thread thread = new Thread(webServer, "[" + counter + "]");
                thread.start();
                counter += 1;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HashMap<HandlerKey, RouteHandler> extractRoutes(List<Object> controllers) {
        HashMap<HandlerKey, RouteHandler> map = new HashMap<>();
        for (Object controller : controllers) {
            logger.info("Scanning routes for controller " + controller.getClass().getName());
            Class<?> objectClass = controller.getClass();
            Method[] methods = objectClass.getMethods();
            for (Method method : methods) {
                Route route = method.getAnnotation(Route.class);
                if (route != null) {
                    logger.info("Found route " + method.getName());
                    map.put(new HandlerKey(route.method(), route.value()), new RouteHandler(method, controller, objectClass));
                }
            }
        }
        return map;
    }
}
