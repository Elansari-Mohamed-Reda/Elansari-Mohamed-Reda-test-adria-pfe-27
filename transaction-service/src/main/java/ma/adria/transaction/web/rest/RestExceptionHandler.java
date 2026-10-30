package ma.adria.transaction.web.rest;

import jakarta.validation.ConstraintViolationException;
import ma.adria.transaction.service.exception.TransactionNotFoundException;
import ma.adria.transaction.service.exception.TransactionRejectedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ma.adria.transaction.service.exception.TransactionConflictException.class)
    public ProblemDetail movementConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT,"Conflit d'identifiant",exception.getMessage());
    }
    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail accountNotFound(TransactionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Transaction introuvable", exception.getMessage());
    }

    @ExceptionHandler(TransactionRejectedException.class)
    public ProblemDetail invalidAccount(TransactionRejectedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction refusee", exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidServiceArguments(ConstraintViolationException exception) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Un ou plusieurs parametres sont invalides.");
        Map<String, String> errors = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            errors.merge(field, violation.getMessage(), (first, second) -> first + "; " + second);
        });
        body.setProperty("errors", errors);
        return body;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrentModification(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Modification concurrente",
                "Le compte a ete modifie par une autre operation. Rechargez-le avant de recommencer.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Requete invalide",
                "Un ou plusieurs champs sont invalides.");
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), error.getDefaultMessage(),
                        (first, second) -> first + "; " + second));
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Corps JSON invalide",
                "Le corps JSON est absent, mal forme ou contient une valeur incompatible.");
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Parametre invalide",
                "Le format d'un parametre est invalide.");
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }
}
