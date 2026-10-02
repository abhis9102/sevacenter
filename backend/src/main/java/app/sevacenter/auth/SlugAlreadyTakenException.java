package app.sevacenter.auth;

/** Thrown when a requested tenant slug (subdomain) is already registered. */
public class SlugAlreadyTakenException extends RuntimeException {
    public SlugAlreadyTakenException(String slug) {
        super("slug already taken: " + slug);
    }
}
