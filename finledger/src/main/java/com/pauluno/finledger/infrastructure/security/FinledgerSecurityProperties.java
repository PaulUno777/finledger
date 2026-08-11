package com.pauluno.finledger.infrastructure.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finledger.security")
public class FinledgerSecurityProperties {

    private String issuer = "external";
    private Duration maxTokenTtl = Duration.ofMinutes(15);
    private Duration maxTokenTtlMachine = Duration.ofHours(1);
    private List<String> machineAzpAllowlist = new ArrayList<>();
    private List<String> issuerAliases = new ArrayList<>();
    private final Claim claim = new Claim();
    private Map<String, String> scopeAliases = new LinkedHashMap<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getMaxTokenTtl() {
        return maxTokenTtl;
    }

    public void setMaxTokenTtl(Duration maxTokenTtl) {
        this.maxTokenTtl = maxTokenTtl;
    }

    public Duration getMaxTokenTtlMachine() {
        return maxTokenTtlMachine;
    }

    public void setMaxTokenTtlMachine(Duration maxTokenTtlMachine) {
        this.maxTokenTtlMachine = maxTokenTtlMachine;
    }

    public List<String> getMachineAzpAllowlist() {
        return machineAzpAllowlist;
    }

    public void setMachineAzpAllowlist(List<String> machineAzpAllowlist) {
        this.machineAzpAllowlist = machineAzpAllowlist == null ? new ArrayList<>() : machineAzpAllowlist;
    }

    public List<String> getIssuerAliases() {
        return issuerAliases;
    }

    public void setIssuerAliases(List<String> issuerAliases) {
        this.issuerAliases = issuerAliases == null ? new ArrayList<>() : issuerAliases;
    }

    public Claim getClaim() {
        return claim;
    }

    public Map<String, String> getScopeAliases() {
        return scopeAliases;
    }

    public void setScopeAliases(Map<String, String> scopeAliases) {
        this.scopeAliases = scopeAliases == null ? new LinkedHashMap<>() : scopeAliases;
    }

    public static class Claim {
        private String tenantId = "tenant_id";
        private String scopes = "scope";

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }
    }
}
