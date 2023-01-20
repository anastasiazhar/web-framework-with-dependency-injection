package app;


public class F {

    private final D d;
    private String value;
    private final E e;

    public F(D d, String value, E e) {
        this.d = d;
        this.value = value;
        this.e = e;
    }

    public String sayF() {
        return "fff " + value + " " + d.sayD() + " " + e.sayE();
    }
}
