package mx.marjan.api;

import io.micronaut.runtime.Micronaut;

/** Entry point of the SysPort MARJAN REST API. */
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
