package app;

import webdi.annotation.Controller;
import webdi.annotation.Inject;
import webdi.annotation.Route;

@Controller
public class AnotherController {

    private A a;

    @Inject
    public AnotherController(A a) {
        this.a = a;
    }

    @Route("/smth")
    public String smth() {
        return "smth " + a.getA();
    }
}
