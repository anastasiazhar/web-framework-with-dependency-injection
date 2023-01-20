package app;

import webdi.annotation.Component;
import webdi.annotation.Inject;

@Component
public class A {

    private B b;
    private C c;

    @Inject
    public A(B b, C c) {
        this.b = b;
        this.c = c;
    }

    public String getA() {
        return "hi i'm A, B said: " + b.getB() + "..." + c.getC();
    }
}
