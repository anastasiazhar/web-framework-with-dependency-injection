package app;

import webdi.annotation.Component;
import webdi.annotation.Inject;

@Component
public class MyInterfaceImplementation implements MyInterface {
    B b;
    E e;

    @Inject
    public MyInterfaceImplementation(B b, E e) {
        this.b = b;
        this.e = e;
    }

    @Override
    public String myMethod() {
        return b.getB() + " " + e.sayE();
    }
}
