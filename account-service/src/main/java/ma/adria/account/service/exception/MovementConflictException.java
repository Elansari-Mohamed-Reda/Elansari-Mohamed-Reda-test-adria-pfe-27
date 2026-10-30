package ma.adria.account.service.exception;
public class MovementConflictException extends RuntimeException {
 public MovementConflictException() { super("Identifiant de mouvement deja utilise avec un contenu different."); }
}
