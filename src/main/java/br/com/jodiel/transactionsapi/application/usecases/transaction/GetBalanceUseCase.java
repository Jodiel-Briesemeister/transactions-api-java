package br.com.jodiel.transactionsapi.application.usecases.transaction;

import br.com.jodiel.transactionsapi.application.dtos.transaction.BalanceResponse;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetBalanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetBalanceUseCase.class);

    private final AccountRepository accountRepository;

    public GetBalanceUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public BalanceResponse execute(String userId) {
        return accountRepository.findByUserId(userId)
                .map(a -> new BalanceResponse(a.getBalance()))
                .orElseThrow(() -> {
                    log.error("Account not found userId={}", userId);
                    return new AppException("Account not found", 404);
                });
    }
}
