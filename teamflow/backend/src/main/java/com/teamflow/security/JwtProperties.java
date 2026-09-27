package com.teamflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed binding for security.jwt.* configuration, instead of scattering
 * System.getenv()/@Value calls through business code. */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 3_600_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
