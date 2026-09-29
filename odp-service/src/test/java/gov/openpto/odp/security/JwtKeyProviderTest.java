package gov.openpto.odp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import gov.openpto.odp.config.AppProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeyProviderTest {

    @TempDir
    Path dir;

    @Test
    void generatesOnFirstStart_andReloadsSameKeyOnRestart() throws Exception {
        Path keyDir = dir.resolve("nested/keys");
        JwtKeyProvider first = new JwtKeyProvider(new AppProperties.Jwt("openpto", Duration.ofHours(8), keyDir.toString()));

        assertThat(keyDir.resolve(JwtKeyProvider.PRIVATE_KEY_FILE)).exists();
        assertThat(Files.readString(keyDir.resolve(JwtKeyProvider.PUBLIC_KEY_FILE))).startsWith("-----BEGIN PUBLIC KEY-----");

        JwtKeyProvider second = new JwtKeyProvider(new AppProperties.Jwt("openpto", Duration.ofHours(8), keyDir.toString()));

        assertThat(second.signingKey().getKeyID()).isEqualTo(first.signingKey().getKeyID());
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
        RSAKey published = (RSAKey) first.publicJwkSet().getKeys().getFirst();
        assertThat(published.isPrivate()).isFalse();
        assertThat(published.getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(published.getKeyUse().identifier()).isEqualTo("sig");
    }
}
