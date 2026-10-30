package ma.adria.transaction.service.exception;
public class TransactionConflictException extends RuntimeException {
 public TransactionConflictException() { super("requestId deja utilise avec un contenu different."); }
}
