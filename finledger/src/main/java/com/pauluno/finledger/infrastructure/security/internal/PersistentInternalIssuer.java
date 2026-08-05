package com.pauluno.finledger.infrastructure.security.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Durable RSA issuer for {@code normal} + {@code issuer=internal} (FL-156 / ADR-016).
 * Clients are tenant-bound; secrets and signing key must be supplied (never auto-generated).
 */
public final class PersistentInternalIssuer implements InternalJwtIssuer {

    public static final String DEFAULT_ISSUER = "http://localhost:8080/internal";

    private final RsaInternalJwtSupport support;
    private final Map<String, InternalClientCredentials> clientsById;

    private PersistentInternalIssuer(
            RsaInternalJwtSupport support,
            Map<String, InternalClientCredentials> clientsById
    ) {
        this.support = support;
        this.clientsById = Map.copyOf(clientsById);
    }

    public static PersistentInternalIssuer fromPemAndClients(
            String issuer,
            String privateKeyPem,
            String publicKeyPem,
            Collection<InternalClientCredentials> clients,
            Duration maxTokenTtl
    ) {
        Objects.requireNonNull(privateKeyPem, "privateKeyPem");
        if (privateKeyPem.isBlank()) {
            throw new IllegalArgumentException(
                    "finledger.security.internal signing key PEM must not be blank "
                            + "(set FINLEDGER_INTERNAL_SIGNING_KEY_PEM or signing-key-path)");
        }
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException(
                    "finledger.security.internal.clients must contain at least one client");
        }
        String resolvedIssuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer.trim();
        RSAKey rsaKey = buildRsaKey(privateKeyPem, publicKeyPem);
        Map<String, InternalClientCredentials> byId = new LinkedHashMap<>();
        for (InternalClientCredentials client : clients) {
            if (byId.put(client.clientId(), client) != null) {
                throw new IllegalArgumentException("Duplicate client-id: " + client.clientId());
            }
        }
        return new PersistentInternalIssuer(
                new RsaInternalJwtSupport(rsaKey, resolvedIssuer, maxTokenTtl),
                byId);
    }

    public static String readPemFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read signing key from " + path, ex);
        }
    }

    public List<InternalClientCredentials> clients() {
        return List.copyOf(clientsById.values());
    }

    @Override
    public String issuer() {
        return support.issuer();
    }

    @Override
    public Duration maxTokenTtl() {
        return support.maxTokenTtl();
    }

    @Override
    public JwtDecoder jwtDecoder() {
        return support.jwtDecoder();
    }

    @Override
    public Map<String, Object> jwks() {
        return support.jwks();
    }

    @Override
    public AccessToken mintAccessToken(String clientId, String clientSecret) {
        InternalClientCredentials client = clientsById.get(clientId);
        if (client == null || !client.clientSecret().equals(clientSecret)) {
            throw new InvalidClientCredentialsException("Invalid client_id or client_secret");
        }
        return support.mint(client);
    }

    private static RSAKey buildRsaKey(String privateKeyPem, String publicKeyPem) {
        try {
            RSAPrivateKey privateKey = parsePrivateKey(privateKeyPem);
            RSAPublicKey publicKey;
            if (publicKeyPem != null && !publicKeyPem.isBlank()) {
                publicKey = parsePublicKey(publicKeyPem);
            } else {
                // Derive public modulus/exponent from PKCS#8 private key via KeyFactory round-trip
                // is not available for RSAPrivateKey alone — require CRT or public PEM.
                // Use RSAPrivateCrtKey when present.
                if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
                    publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                            new java.security.spec.RSAPublicKeySpec(
                                    crt.getModulus(), crt.getPublicExponent()));
                } else {
                    throw new IllegalArgumentException(
                            "Public key PEM is required when the private key is not an RSA CRT key");
                }
            }
            RSAKey key = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
            return RsaInternalJwtSupport.requireKeyId(key);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse RSA signing key PEM", ex);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = stripPem(pem, "PRIVATE KEY");
        byte[] der = Base64.getDecoder().decode(normalized);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String normalized = stripPem(pem, "PUBLIC KEY");
        byte[] der = Base64.getDecoder().decode(normalized);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private static String stripPem(String pem, String label) {
        String trimmed = pem.replace("\\n", "\n").trim();
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        if (!trimmed.contains(begin)) {
            // Also accept RSA PRIVATE KEY (PKCS#1) — reject with clear message
            if (trimmed.contains("BEGIN RSA PRIVATE KEY")) {
                throw new IllegalArgumentException(
                        "PKCS#1 RSA PRIVATE KEY is not supported; convert to PKCS#8 "
                                + "(openssl pkcs8 -topk8 -nocrypt -in key.pem)");
            }
            throw new IllegalArgumentException("PEM must contain " + begin);
        }
        return trimmed
                .replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s", "");
    }
}
