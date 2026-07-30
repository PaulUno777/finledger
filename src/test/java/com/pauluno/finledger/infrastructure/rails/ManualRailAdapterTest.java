package com.pauluno.finledger.infrastructure.rails;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.rail.RailTransactionRequest;
import com.pauluno.finledger.application.rail.RailTransactionResult;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;

@Tag("unit")
class ManualRailAdapterTest {

    @Test
    void should_initiate_as_initiated_and_check_status() {
        ManualRailAdapter adapter = new ManualRailAdapter();
        RailTransactionResult result = adapter.initiate(new RailTransactionRequest(
                UUID.randomUUID(),
                "MANUAL",
                new BigDecimal("10.00"),
                Currency.getInstance("USD"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "client-1"
        ));

        assertThat(result.status()).isEqualTo(RailSettlementStatus.INITIATED);
        assertThat(result.railReference()).startsWith("manual-");
        assertThat(adapter.checkStatus(result.railReference())).isEqualTo(RailSettlementStatus.INITIATED);
        assertThat(adapter.checkStatus("unknown")).isEqualTo(RailSettlementStatus.FAILED);
    }
}
