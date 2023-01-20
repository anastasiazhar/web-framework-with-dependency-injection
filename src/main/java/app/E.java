package app;

public class E {

    private final D d;
    private final String value;


    public E(D d, String value) {
        this.d = d;
        this.value = value;
    }

    public String sayE() {
        return "eee " + value + " " + d.sayD();
    }
}
