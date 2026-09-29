package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "ada@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "correct-horse-7battery", description = "Min 10 chars, at least one letter and one digit")
        @NotBlank
        @Size(min = 10, max = 128)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String password,
        @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 100) String displayName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
