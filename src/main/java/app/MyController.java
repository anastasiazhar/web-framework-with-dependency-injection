package app;

import webdi.annotation.*;

@Controller
public class MyController {
    private final String homemessage;
    private final String hometext;
    private final C c;
    private final Integer i;
    private final Boolean b;
    private final double d;
    private final String exampleA;
    private final String exampleB;


    @Inject
    public MyController(
            @Value("homemessage") String homemessage,
            C c,
            @Value("hometext") String hometext,
            @Value("i") int i,
            @Value("b") Boolean b,
            @Value("double") double d,
            @Named("example-a") String exampleA,
            @Named("example-b") String exampleB
    ) {
        this.homemessage = homemessage;
        this.c = c;
        this.hometext = hometext;
        this.i = i;
        this.b = b;
        this.d = d;
        this.exampleA = exampleA;
        this.exampleB = exampleB;
    }

    @Route("/home")
    public String homePage() {
        return "<h1>" + homemessage + "</h1><p>" + hometext + ", " + i + ", " + b + ", " + d + "</p>";
    }

    @Route("/porn")
    public String pornPage() {
        return "<h1>" + pornContent() + "</h1>" + "<p>" + c.getC() + "</p>";
    }

    @Route("/furry")
    public String furryPage() {
        return "<p>" + exampleA + "</p><p>" + exampleB + "</p>";
    }

    public String pornContent() {
        return "porn content";
    }
}
