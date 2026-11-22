package com.maheshshinde.CryptEnv.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Signup-grade email validation: syntax, common typo detection, and a cached
 * MX/A record lookup. DNS failures fail open so a resolver outage cannot block sign-ups.
 */
@Service
@Slf4j
public class EmailAddressService {

    private static final Pattern ADDRESS = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final Map<String, String> TYPO_DOMAINS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"),
            Map.entry("gmai.com", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.co", "gmail.com"),
            Map.entry("gmail.comm", "gmail.com"),
            Map.entry("yaho.com", "yahoo.com"),
            Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("hotmial.com", "hotmail.com"),
            Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("outlok.com", "outlook.com"),
            Map.entry("outloo.com", "outlook.com"),
            Map.entry("icloud.con", "icloud.com"),
            Map.entry("protonmai.com", "protonmail.com")
    );

    @Value("${CRYPTENV_EMAIL_DNS_CHECK:true}")
    private boolean dnsCheckEnabled;

    private final Map<String, CachedDomain> domainCache = new ConcurrentHashMap<>();
    private static final long CACHE_MILLIS = 10 * 60 * 1000L;

    public record Result(boolean valid, String normalized, String suggestion, String reason) {}

    public Result check(String rawEmail) {
        String email = normalize(rawEmail);
        if (email == null || email.isEmpty()) {
            return new Result(false, null, null, "Email is required");
        }
        if (email.length() > 100 || !ADDRESS.matcher(email).matches()
                || email.contains("..") || email.startsWith(".") || email.contains(".@")
                || email.contains("@.")) {
            return new Result(false, email, null, "Enter a valid email address");
        }

        String domain = email.substring(email.indexOf('@') + 1);
        String suggestion = suggestionFor(domain);
        if (suggestion != null) {
            String local = email.substring(0, email.indexOf('@'));
            return new Result(true, email, local + "@" + suggestion,
                    "Did you mean " + local + "@" + suggestion + "?");
        }

        if (dnsCheckEnabled && !domainAcceptsMail(domain)) {
            return new Result(false, email, null,
                    "The domain " + domain + " does not appear to accept email. Check the spelling.");
        }
        return new Result(true, email, null, null);
    }

    /** Throws a client-facing error when the address cannot receive mail. */
    public String requireDeliverable(String rawEmail) {
        Result result = check(rawEmail);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.reason());
        }
        return result.normalized();
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String suggestionFor(String domain) {
        return TYPO_DOMAINS.get(domain);
    }

    private boolean domainAcceptsMail(String domain) {
        CachedDomain cached = domainCache.get(domain);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached.valid();
        }
        boolean valid = lookup(domain);
        domainCache.put(domain, new CachedDomain(valid, now + CACHE_MILLIS));
        return valid;
    }

    private boolean lookup(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes mx = ctx.getAttributes(domain, new String[]{"MX"});
            if (mx.get("MX") != null && mx.get("MX").size() > 0) {
                // A single "0 ." record is the RFC 7505 null MX used to refuse mail.
                String value = String.valueOf(mx.get("MX").get(0));
                if (!value.trim().equals("0 .")) return true;
            }
            Attributes a = ctx.getAttributes(domain, new String[]{"A"});
            return a.get("A") != null && a.get("A").size() > 0;
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            log.warn("Email domain lookup failed for {} ({}); allowing signup", domain, e.getMessage());
            return true;
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ignored) { }
            }
        }
    }

    private record CachedDomain(boolean valid, long expiresAt) {}
}