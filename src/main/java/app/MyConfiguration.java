package app;

import webdi.annotation.Bean;
import webdi.annotation.Configuration;
import webdi.annotation.Named;
import webdi.annotation.Value;

@Configuration
public class MyConfiguration {

    @Bean
    public D createD(@Value("d") String value) {
        return new D(value);
    }

    @Bean
    public E createE(D d, @Value("e") String value) {
        return new E(d, value);
    }

    @Bean
    public F createF(D d, @Value("f") String value, E e) {
        return new F(d, value, e);
    }

    @Bean
    @Named("example-a")
    public String exampleA() {
        return "Example A";
    }
    @Bean
    @Named("example-b")
    public String exampleB() {
        return "Example B";
    }
}
