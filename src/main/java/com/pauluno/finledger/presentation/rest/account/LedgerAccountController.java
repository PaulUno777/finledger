package com.pauluno.finledger.presentation.rest.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.CreateLedgerAccountCommand;
import com.pauluno.finledger.application.dto.CreateLedgerAccountResult;
import com.pauluno.finledger.application.port.in.CreateLedgerAccountUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/accounts")
public class LedgerAccountController {

    private final CreateLedgerAccountUseCase createLedgerAccountUseCase;

    public LedgerAccountController(CreateLedgerAccountUseCase createLedgerAccountUseCase) {
        this.createLedgerAccountUseCase = createLedgerAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateLedgerAccountResult> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        CreateLedgerAccountResult result = createLedgerAccountUseCase.execute(
                new CreateLedgerAccountCommand(
                        tenantId,
                        request.ownerRef(),
                        request.currencyCode(),
                        request.type(),
                        Boolean.TRUE.equals(request.allowsOverdraft())
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    public record CreateAccountRequest(
            @NotBlank String ownerRef,
            @NotBlank String currencyCode,
            @NotBlank String type,
            @NotNull Boolean allowsOverdraft
    ) {
    }
}
