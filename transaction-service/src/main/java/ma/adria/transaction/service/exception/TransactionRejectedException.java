package ma.adria.transaction.service.exception;
public class TransactionRejectedException extends RuntimeException {
    public TransactionRejectedException(String message) { super(message); }
}
