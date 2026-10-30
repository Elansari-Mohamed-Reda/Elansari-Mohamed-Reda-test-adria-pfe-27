package ma.adria.transaction.service;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import ma.adria.transaction.service.dto.*;
import ma.adria.transaction.service.exception.TransactionRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TransactionPolicy {
    private final BigDecimal maxAmount;
    private final Validator validator;
    public TransactionPolicy(@Value("${bank.transaction.max-amount:1000}") BigDecimal maxAmount, Validator validator) {
        if (maxAmount == null || maxAmount.signum() <= 0) throw new IllegalArgumentException("Plafond invalide.");
        this.maxAmount=maxAmount;
        this.validator=validator;
    }
    public void validate(CreateTransactionCommand command, AccountSnapshot account) {
        if (command==null) throw new TransactionRejectedException("Transaction obligatoire.");
        var violations=validator.validate(command);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (command.amount().compareTo(maxAmount)>0) reject("Plafond depasse.");
        if (account==null || !command.accountId().equals(account.id())) reject("Compte introuvable ou reference incoherente.");
        if (!"ACTIVE".equals(account.status())) reject("Le compte doit etre actif.");
        if (!command.currency().equals(account.currency())) reject("Devises incompatibles.");
        try {
            int scale=Currency.getInstance(command.currency()).getDefaultFractionDigits();
            if(scale<0 || scale>4) reject("Devise non prise en charge.");
            command.amount().setScale(scale,RoundingMode.UNNECESSARY);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            reject("Devise ou precision du montant invalide.");
        }
        if (account.balance()==null || account.balance().compareTo(command.amount())<0) reject("Solde insuffisant.");
    }
    private void reject(String message) { throw new TransactionRejectedException(message); }
}
