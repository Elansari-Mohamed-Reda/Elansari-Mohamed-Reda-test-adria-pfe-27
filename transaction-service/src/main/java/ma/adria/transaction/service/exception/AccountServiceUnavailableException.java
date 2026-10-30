package ma.adria.transaction.service.exception;
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(Throwable cause) {
        super("Account-Service temporairement indisponible.",cause);
    }
}
