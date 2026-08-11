package com.pauluno.finledger.presentation.rest.platform;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.ProvisionPlatformCommand;
import com.pauluno.finledger.application.dto.ProvisionPlatformResult;
import com.pauluno.finledger.application.port.in.ProvisionPlatformUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformProvisionController {

    private final ProvisionPlatformUseCase provisionPlatformUseCase;

    public PlatformProvisionController(ProvisionPlatformUseCase provisionPlatformUseCase) {
        this.provisionPlatformUseCase = provisionPlatformUseCase;
    }

    @PostMapping("/provision")
    public ResponseEntity<ProvisionPlatformResult> provision(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ProvisionRequest request
    ) {
        ProvisionPlatformResult result = provisionPlatformUseCase.execute(new ProvisionPlatformCommand(
                request.recipe(),
                request.name(),
                request.tenantId(),
                request.currencyCode(),
                idempotencyKey
        ));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    public record ProvisionRequest(
            @NotBlank String recipe,
            String name,
            UUID tenantId,
            String currencyCode
    ) {
    }
}
