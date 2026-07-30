package com.pauluno.finledger.presentation.rest.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.CreateTenantCommand;
import com.pauluno.finledger.application.dto.CreateTenantResult;
import com.pauluno.finledger.application.port.in.CreateTenantUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final CreateTenantUseCase createTenantUseCase;

    public TenantController(CreateTenantUseCase createTenantUseCase) {
        this.createTenantUseCase = createTenantUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateTenantResult> create(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantResult result = createTenantUseCase.execute(
                new CreateTenantCommand(request.name(), request.type(), request.parentTenantId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    public record CreateTenantRequest(
            @NotBlank String name,
            @NotBlank String type,
            UUID parentTenantId
    ) {
    }
}
