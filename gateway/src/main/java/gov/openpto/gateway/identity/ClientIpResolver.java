package gov.openpto.gateway.identity;

import gov.openpto.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Determines the real client IP. {@code X-Forwarded-For} is honoured only when the direct peer is a
 * configured trusted proxy ({@code gateway.trusted-proxies}: IPs or CIDRs, default none), in which case
 * the right-most untrusted hop wins. This stops clients from picking their own rate-limit bucket.
 */
@Component
public class ClientIpResolver {

    private final List<Cidr> trusted;

    @Autowired
    public ClientIpResolver(GatewayProperties properties) {
        this(properties.trustedProxies());
    }

    ClientIpResolver(List<String> trustedProxies) {
        this.trusted = trustedProxies.stream().filter(s -> !s.isBlank()).map(Cidr::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!isTrusted(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        List<String> hops = new ArrayList<>(Arrays.stream(forwarded.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = normalize(hops.get(i));
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return hops.isEmpty() ? remote : normalize(hops.getFirst());
    }

    /** True when the address is a configured trusted proxy. */
    public boolean isTrusted(String address) {
        if (trusted.isEmpty() || address == null) {
            return false;
        }
        byte[] bytes = literalBytes(address);
        return bytes != null && trusted.stream().anyMatch(c -> c.contains(bytes));
    }

    private static String normalize(String address) {
        if (address == null) {
            return "unknown";
        }
        byte[] bytes = literalBytes(address);
        if (bytes == null) {
            return address;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return address;
        }
    }

    /** Parses an IP literal without DNS lookups; returns null for anything that is not a literal. */
    static byte[] literalBytes(String address) {
        String a = address.trim();
        if (a.startsWith("[") && a.endsWith("]")) {
            a = a.substring(1, a.length() - 1);
        }
        if (a.isEmpty() || !(a.contains(":") || a.matches("[0-9.]+"))) {
            return null;
        }
        if (!a.matches("[0-9a-fA-F:.%]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(a).getAddress();
        } catch (UnknownHostException | SecurityException e) {
            return null;
        }
    }

    private record Cidr(byte[] network, int prefix) {

        static Cidr parse(String spec) {
            String[] parts = spec.trim().split("/", 2);
            byte[] addr = literalBytes(parts[0]);
            if (addr == null) {
                throw new IllegalArgumentException("gateway.trusted-proxies: not an IP/CIDR: " + spec);
            }
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : addr.length * 8;
            return new Cidr(addr, prefix);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int rem = prefix % 8;
            if (rem == 0) {
                return true;
            }
            int mask = (0xFF << (8 - rem)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
