package app;

import webdi.annotation.Controller;
import webdi.annotation.Inject;
import webdi.annotation.Route;

@Controller
public class AnotherController {

    private A a;
    private F f;

    @Inject
    public AnotherController(A a, F f) {
        this.a = a;
        this.f = f;
    }

    @Route("/smth")
    public String smth() {
        return "smth " + a.getA() + " " + f.sayF();
    }
}
