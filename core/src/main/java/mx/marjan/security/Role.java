package mx.marjan.security;

public record Role(long id, String name) {

    @Override
    public String toString() {
        return name;
    }
}
