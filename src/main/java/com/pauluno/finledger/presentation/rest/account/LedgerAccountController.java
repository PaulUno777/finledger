package com.pauluno.finledger.presentation.rest.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pauluno.finledger.application.dto.AccountBalanceResult;
import com.pauluno.finledger.application.dto.CreateLedgerAccountCommand;
import com.pauluno.finledger.application.dto.CreateLedgerAccountResult;
import com.pauluno.finledger.application.dto.LedgerAccountResult;
import com.pauluno.finledger.application.port.in.CreateLedgerAccountUseCase;
import com.pauluno.finledger.application.port.in.GetAccountBalanceUseCase;
import com.pauluno.finledger.application.port.in.GetLedgerAccountUseCase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/accounts")
public class LedgerAccountController {

    private final CreateLedgerAccountUseCase createLedgerAccountUseCase;
    private final GetLedgerAccountUseCase getLedgerAccountUseCase;
    private final GetAccountBalanceUseCase getAccountBalanceUseCase;

    public LedgerAccountController(
            CreateLedgerAccountUseCase createLedgerAccountUseCase,
            GetLedgerAccountUseCase getLedgerAccountUseCase,
            GetAccountBalanceUseCase getAccountBalanceUseCase
    ) {
        this.createLedgerAccountUseCase = createLedgerAccountUseCase;
        this.getLedgerAccountUseCase = getLedgerAccountUseCase;
        this.getAccountBalanceUseCase = getAccountBalanceUseCase;
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

    @GetMapping("/{accountId}")
    public ResponseEntity<LedgerAccountResult> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(getLedgerAccountUseCase.execute(tenantId, accountId));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResult> getBalance(
            @PathVariable UUID tenantId,
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(getAccountBalanceUseCase.execute(tenantId, accountId));
    }

    public record CreateAccountRequest(
            @NotBlank String ownerRef,
            @NotBlank String currencyCode,
            @NotBlank String type,
            @NotNull Boolean allowsOverdraft
    ) {
    }
}
