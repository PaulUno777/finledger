package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_admin_credential")
public class PlatformAdminCredentialEntity {

    @Id
    private Short id = 1;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(nullable = false)
    private UUID jti;

    @Column(name = "bootstrap_secret_sha256")
    private byte[] bootstrapSecretSha256;

    public PlatformAdminCredentialEntity() {
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public UUID getJti() {
        return jti;
    }

    public void setJti(UUID jti) {
        this.jti = jti;
    }

    public byte[] getBootstrapSecretSha256() {
        return bootstrapSecretSha256;
    }

    public void setBootstrapSecretSha256(byte[] bootstrapSecretSha256) {
        this.bootstrapSecretSha256 = bootstrapSecretSha256;
    }
}
