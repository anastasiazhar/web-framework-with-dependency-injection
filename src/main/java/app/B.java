package app;

import webdi.annotation.Component;
import webdi.annotation.Inject;

@Component
public class B {

    private C c;

    @Inject
    public B(C c) {
        this.c = c;
    }

    public String getB() {
        return "i'm B - Big dick, C said: " + c.getC();
    }

    public C getC() {
        return c;
    }

    public void setC(C c) {
        this.c = c;
    }
}
