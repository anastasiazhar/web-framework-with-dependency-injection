package app;

import webdi.annotation.Controller;
import webdi.annotation.Inject;
import webdi.annotation.Route;

@Controller(contentType = "text/html")
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

    @Route(value = "/json", contentType = "application/json")
    public String jsonRoute() {
        return """
                {
                    "glossary": {
                        "title": "example glossary",
                		"GlossDiv": {
                            "title": "S",
                			"GlossList": {
                                "GlossEntry": {
                                    "ID": "SGML",
                					"SortAs": "SGML",
                					"GlossTerm": "Standard Generalized Markup Language",
                					"Acronym": "SGML",
                					"Abbrev": "ISO 8879:1986",
                					"GlossDef": {
                                        "para": "A meta-markup language, used to create markup languages such as DocBook.",
                						"GlossSeeAlso": ["GML", "XML"]
                                    },
                					"GlossSee": "markup"
                                }
                            }
                        }
                    }
                }
                """;
    }

    @Route(value = "/object", contentType = "application/json")
    public MyClass objectRoute() {
        return new MyClass("a", 1);
    }
}
