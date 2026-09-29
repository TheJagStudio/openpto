package gov.openpto.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void resolve_defaultTrustsNobody() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        MockHttpServletRequest request = request("127.0.0.1", "6.6.6.6");
        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
        assertThat(resolver.isTrusted("127.0.0.1")).isFalse();
    }

    @Test
    void resolve_trustedChain_returnsRightMostUntrusted() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8", "192.168.1.1"));
        assertThat(resolver.resolve(request("192.168.1.1", "6.6.6.6, 7.7.7.7, 10.2.3.4"))).isEqualTo("7.7.7.7");
        assertThat(resolver.resolve(request("10.0.0.5", "10.0.0.6"))).isEqualTo("10.0.0.6");
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", " , "))).isEqualTo("10.0.0.5");
    }

    @Test
    void cidr_prefixesAndIpv6() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("172.16.0.0/12", "::1", "fd00::/8"));
        assertThat(resolver.isTrusted("172.31.255.255")).isTrue();
        assertThat(resolver.isTrusted("172.32.0.1")).isFalse();
        assertThat(resolver.isTrusted("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(resolver.isTrusted("fd12::5")).isTrue();
        assertThat(resolver.isTrusted("example.com")).isFalse();
        assertThat(resolver.resolve(request("::1", "[2001:db8::1]"))).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void invalidTrustedProxy_failsFast() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("proxy.local")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest request(String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
