package app;

import webdi.annotation.*;
import webdi.web.NamedFile;
import webdi.web.ResponseCookie;
import webdi.web.ResponseEntity;
import webdi.web.Status;

import java.io.ByteArrayOutputStream;

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

    @Route(value = "/image", contentType = "image/jpeg")
    public NamedFile imageRoute() {
        return new NamedFile("image.jpg");
    }

    @Route("/test")
    public ResponseEntity responseEntityRoute() {
        return ResponseEntity.of("i fuck your mom and she likes it", Status.NOT_IMPLEMENTED);
    }

    @Route("/empty")
    public ResponseEntity emptyBodyRoute() {
        return ResponseEntity.noContent();
    }

    @Route("/headers")
    public ResponseEntity returnHeaders(@Header("User-Agent") String userAgent) {
        return ResponseEntity.Builder.newInstance()
                .body("i fuck your mom using " + userAgent)
                .header("X", "XXX").build();
    }

    @Route(method = "POST",value = "/body", contentType = "text/plain")
    public String testBody(@BodyParam ByteArrayOutputStream stream) {
        return "Hello, " + stream.toString();
    }

    @Route(method = "POST", value = "/json", contentType = "application/json")
    public MyClass bodyToJson(@BodyParam MyClass object) {
        object.setN(8);
        return object;
    }

    @Route("/error")
    public String error(@Value("") String d) {
        return "d " + d;
    }

    @Route("/query")
    public String queryTest(@QueryParam("a") String a, @QueryParam("b") int b) {
        return "a is " + a + ", b is " + b;
    }

    @Route("/trie")
    public String trieTest() {
        return "hi";
    }

    @Route("/trie/example")
    public String trieTestExample() {
        return "example";
    }

    @Route("/trie/{name}")
    public String trieTestName(@PathParam("name") String name) {
        return "hi, " + name;
    }

    @Route("/trie/{name}/smth")
    public String trieTestSmth(@PathParam("name") String name) {
        return "another hi, " + name;
    }

    @Route("/trie/{id:[1-9][0-9]*}/word")
    public String trieTestWord(@PathParam("id") int id) {
        return "hello, id " + id;
    }

    @Route("/cookies")
    public ResponseEntity cookies(@Cookie("my-cookie") String cookie) {
        if (cookie == null) {
            return ResponseEntity.Builder.newInstance()
                    .status(Status.OK)
                    .cookie(new ResponseCookie("my-cookie", "hello bitches " + System.currentTimeMillis(), "/", 60))
                    .body("you had no cookie, you shall not pass")
                    .build();
        } else {
            return ResponseEntity.Builder.newInstance()
                    .status(Status.OK)
                    .body("yay, you have a cookie " + cookie)
                    .build();
        }
    }
}
