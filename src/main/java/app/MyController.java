package app;

import webdi.annotation.Controller;
import webdi.annotation.Inject;
import webdi.annotation.Route;
import webdi.annotation.Value;

@Controller
public class MyController {
    private final String homemessage;
    private final String hometext;
    private final C c;

    @Inject
    public MyController(@Value("homemessage") String homemessage, C c, @Value("hometext") String hometext) {
        this.homemessage = homemessage;
        this.c = c;
        this.hometext = hometext;
    }

    @Route("/home")
    public String homePage() {
        return "<h1>" + homemessage + "</h1><p>" + hometext + "</p>";
    }

    @Route("/porn")
    public String pornPage() {
        return "<h1>" + pornContent() + "</h1>" + "<p>" + c.getC() + "</p>";
    }

    public String pornContent() {
        return "porn content";
    }
}
