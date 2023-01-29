package app;

import webdi.annotation.Controller;
import webdi.annotation.Inject;
import webdi.annotation.Route;

@Controller
public class AnotherController {

    private A a;
    private F f;
    private MyInterfaceImplementation myInterfaceImplementation;

    @Inject
    public AnotherController(A a, F f, MyInterfaceImplementation myInterfaceImplementation) {
        this.a = a;
        this.f = f;
        this.myInterfaceImplementation = myInterfaceImplementation;
    }

    @Route("/smth")
    public String smth() {
        return "smth " + a.getA() + " " + f.sayF();
    }
}
