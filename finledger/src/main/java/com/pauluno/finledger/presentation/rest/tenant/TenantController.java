package com.pauluno.finledger.presentation.rest.tenant;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;
import com.pauluno.finledger.application.port.in.CreateTenantUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private static final String SCOPE_PLATFORM_ADMIN = "SCOPE_platform:admin";

    private final CreateTenantUseCase createTenantUseCase;

    public TenantController(CreateTenantUseCase createTenantUseCase) {
        this.createTenantUseCase = createTenantUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateTenantResult> create(@Valid @RequestBody CreateTenantRequest request) {
        UUID requestedId = request.id();
        if (requestedId != null && !hasPlatformAdmin()) {
            throw new IllegalArgumentException(
                    "id must not be passed without platform:admin scope "
                            + "(error=id_not_allowed)");
        }
        CreateTenantResult result = createTenantUseCase.execute(
                new CreateTenantCommand(
                        request.name(),
                        request.type(),
                        request.parentTenantId(),
                        requestedId)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private static boolean hasPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (SCOPE_PLATFORM_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public record CreateTenantRequest(
            @NotBlank String name,
            @NotBlank String type,
            UUID parentTenantId,
            UUID id
    ) {
    }
}
