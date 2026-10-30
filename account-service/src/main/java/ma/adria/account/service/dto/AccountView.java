package ma.adria.account.service.dto;

import ma.adria.account.domain.AccountStatus;
import ma.adria.account.domain.BankAccount;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(
        UUID id,
        String customerReference,
        AccountStatus status,
        String currency,
        BigDecimal balance
) {
    public static AccountView from(BankAccount account) {
        return new AccountView(account.getId(), account.getCustomerReference(),
                account.getStatus(), account.getCurrency(), account.getBalance());
    }
}
