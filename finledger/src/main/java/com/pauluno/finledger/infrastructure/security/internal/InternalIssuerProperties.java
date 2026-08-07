package com.pauluno.finledger.infrastructure.security.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durable internal issuer settings ({@code normal} + {@code issuer=internal}).
 */
@ConfigurationProperties(prefix = "finledger.security.internal")
public class InternalIssuerProperties {

    private String issuerUri = PersistentInternalIssuer.DEFAULT_ISSUER;
    /** PKCS#8 PEM (may contain literal {@code \n}). */
    private String signingKeyPem = "";
    /** Optional path to PKCS#8 PEM file; used when {@link #signingKeyPem} is blank. */
    private String signingKeyPath = "";
    /** Optional X.509 public key PEM (derived from CRT private key when blank). */
    private String publicKeyPem = "";
    private List<Client> clients = new ArrayList<>();

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getSigningKeyPem() {
        return signingKeyPem;
    }

    public void setSigningKeyPem(String signingKeyPem) {
        this.signingKeyPem = signingKeyPem;
    }

    public String getSigningKeyPath() {
        return signingKeyPath;
    }

    public void setSigningKeyPath(String signingKeyPath) {
        this.signingKeyPath = signingKeyPath;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients != null ? clients : new ArrayList<>();
    }

    public static class Client {
        private String clientId = "";
        private String clientSecret = "";
        private UUID tenantId;
        private String scopes = InternalClientCredentials.DEFAULT_SCOPES;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public void setTenantId(UUID tenantId) {
            this.tenantId = tenantId;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }

        InternalClientCredentials toCredentials() {
            if (tenantId == null) {
                throw new IllegalArgumentException(
                        "finledger.security.internal.clients[].tenant-id is required");
            }
            return new InternalClientCredentials(clientId, clientSecret, tenantId, scopes);
        }
    }
}
