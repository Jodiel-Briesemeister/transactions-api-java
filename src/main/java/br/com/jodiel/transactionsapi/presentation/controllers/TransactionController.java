package br.com.jodiel.transactionsapi.presentation.controllers;

import br.com.jodiel.transactionsapi.application.dtos.transaction.*;
import br.com.jodiel.transactionsapi.application.usecases.transaction.*;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final TransferUseCase transferUseCase;
    private final GetBalanceUseCase getBalanceUseCase;
    private final ListTransactionsUseCase listTransactionsUseCase;

    public TransactionController(DepositUseCase depositUseCase, WithdrawUseCase withdrawUseCase,
                                  TransferUseCase transferUseCase, GetBalanceUseCase getBalanceUseCase,
                                  ListTransactionsUseCase listTransactionsUseCase) {
        this.depositUseCase = depositUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.transferUseCase = transferUseCase;
        this.getBalanceUseCase = getBalanceUseCase;
        this.listTransactionsUseCase = listTransactionsUseCase;
    }

    @GetMapping
    public List<TransactionResponse> list(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return listTransactionsUseCase.execute(userId, type, from, to);
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@AuthenticationPrincipal String userId) {
        return getBalanceUseCase.execute(userId);
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deposit(@AuthenticationPrincipal String userId,
                        @Valid @RequestBody AmountRequest request) {
        depositUseCase.execute(userId, request.amount());
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal String userId,
                         @Valid @RequestBody AmountRequest request) {
        withdrawUseCase.execute(userId, request.amount());
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@AuthenticationPrincipal String userId,
                         @Valid @RequestBody TransferRequest request) {
        transferUseCase.execute(userId, request.recipientEmail(), request.amount());
    }
}
