-- Defence in depth behind the application-level lock. The debit paths read the account with
-- SELECT ... FOR UPDATE before checking the balance, but nothing stops a future code path from
-- forgetting to. This turns that mistake from silent corruption into a failed transaction.

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0);
