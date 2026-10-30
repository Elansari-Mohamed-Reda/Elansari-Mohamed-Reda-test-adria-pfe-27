package ma.adria.transaction.service.exception;
import java.util.UUID;
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID id) { super("Transaction introuvable : " + id); }
}
