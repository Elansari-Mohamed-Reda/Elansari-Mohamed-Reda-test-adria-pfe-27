package ma.adria.account.service.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID accountId) {
        super("Compte introuvable : " + accountId);
    }
}
