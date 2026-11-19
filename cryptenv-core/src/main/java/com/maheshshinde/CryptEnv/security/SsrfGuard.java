package com.maheshshinde.CryptEnv.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Anti-SSRF guard (§10) for outbound webhook delivery.
 * <p>
 * Blocks private, loopback, link-local, multicast and other non-public address ranges. The
 * target host is resolved and validated <em>before every</em> delivery attempt, and delivery
 * never follows redirects — together this closes the common SSRF and DNS-rebinding vectors.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * Validates a webhook URL: requires an http(s) scheme and rejects hosts that resolve to any
     * non-public address. Hostnames are resolved afresh on each call.
     *
     * @return the resolved primary address, for logging
     * @throws SsrfException when the URL is unsupported or resolves to a blocked range
     */
    public static InetAddress assertAllowed(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new SsrfException("Invalid webhook URL: " + rawUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new SsrfException("Only http(s) webhook URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfException("Webhook URL must include a host");
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfException("Webhook host could not be resolved: " + host);
        }
        if (resolved.length == 0) {
            throw new SsrfException("Webhook host did not resolve to any address: " + host);
        }

        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw new SsrfException("Webhook host resolves to a blocked address range: "
                        + address.getHostAddress());
            }
        }
        return resolved[0];
    }

    /** Returns true when the address falls in a range that must never be reached. */
    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            // 100.64.0.0/10 (CGNAT)
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
            // 192.0.0.0/24 (IETF protocol assignments)
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xFF) == 0) {
                return true;
            }
            // 198.18.0.0/15 (benchmarking)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) {
                return true;
            }
            // 240.0.0.0/4 (reserved) and broadcast
            return b0 >= 240;
        }

        if (address instanceof Inet6Address) {
            // fc00::/7 unique-local (fe80::/10 handled by isLinkLocalAddress)
            int first = bytes[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                return true;
            }
            // IPv4-mapped (::ffff:0:0/96) — re-check the embedded IPv4 address
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                try {
                    return isBlocked(InetAddress.getByAddress(new byte[]{
                            bytes[12], bytes[13], bytes[14], bytes[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
