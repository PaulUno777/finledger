package com.pauluno.finledger.application.port.in;

import com.pauluno.finledger.application.dto.ConfigureSplitRulesCommand;
import com.pauluno.finledger.application.dto.SplitRuleSetResult;

public interface ConfigureSplitRulesUseCase {

    SplitRuleSetResult execute(ConfigureSplitRulesCommand command);
}
