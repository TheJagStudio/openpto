package gov.openpto.gateway.identity;

/** Credentials were presented but are not valid (unknown/revoked API key, bad or expired JWT). */
public class InvalidCredentialsException extends RuntimeException {

    private final boolean bearer;

    private InvalidCredentialsException(String message, boolean bearer) {
        super(message);
        this.bearer = bearer;
    }

    public static InvalidCredentialsException apiKey() {
        return new InvalidCredentialsException("The API key is invalid or has been revoked.", false);
    }

    public static InvalidCredentialsException bearer(String reason) {
        return new InvalidCredentialsException("The bearer token is invalid or expired: " + reason, true);
    }

    /** True when the failed credential was a bearer token (response gets a WWW-Authenticate header). */
    public boolean isBearer() {
        return bearer;
    }
}
