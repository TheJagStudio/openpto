package gov.openpto.odp.controller;

import gov.openpto.odp.security.JwtKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Duration;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Identity")
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyProvider keys;

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Public signing keys (RFC 7517) for validating OpenPTO access tokens")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(keys.publicJwkSet().toJSONObject());
    }
}
