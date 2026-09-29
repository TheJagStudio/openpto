package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(example = "eyJraWQiOiJ...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "28800", description = "Seconds until the token expires") long expiresIn,
        UserResponse user) {

    public static AuthResponse bearer(String token, long expiresIn, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresIn, user);
    }
}
