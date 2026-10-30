package ma.adria.account.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ma.adria.account.domain.BankAccount;
import ma.adria.account.repository.AccountRepository;
import ma.adria.account.service.dto.AccountPage;
import ma.adria.account.service.dto.AccountView;
import ma.adria.account.service.dto.ChangeAccountStatusCommand;
import ma.adria.account.service.dto.CreateAccountCommand;
import ma.adria.account.service.exception.AccountNotFoundException;
import ma.adria.account.service.exception.InvalidAccountException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountView createAccount(@NotNull @Valid CreateAccountCommand command) {
        BigDecimal openingBalance = validateCurrencyAndBalance(command.currency(), command.balance());
        BankAccount account = new BankAccount(command.customerReference().strip(),
                command.currency(), openingBalance);
        return AccountView.from(repository.saveAndFlush(account));
    }

    public AccountView getAccount(@NotNull UUID accountId) {
        return AccountView.from(findAccount(accountId));
    }

    public AccountPage getAccounts(@Min(0) int page, @Min(1) @Max(100) int size) {
        Page<AccountView> accounts = repository.findAll(
                PageRequest.of(page, size, Sort.by("id").ascending())).map(AccountView::from);
        return new AccountPage(accounts.getContent(), accounts.getNumber(), accounts.getSize(),
                accounts.getTotalElements(), accounts.getTotalPages());
    }

    @Transactional
    public AccountView changeStatus(@NotNull UUID accountId,
                                    @NotNull @Valid ChangeAccountStatusCommand command) {
        BankAccount account = findAccount(accountId);
        account.changeStatus(command.status());
        return AccountView.from(account);
    }

    private BankAccount findAccount(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private BigDecimal validateCurrencyAndBalance(String currencyCode, BigDecimal balance) {
        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccountException("Devise ISO 4217 inconnue : " + currencyCode);
        }

        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0 || fractionDigits > 4) {
            throw new InvalidAccountException("Devise non prise en charge : " + currencyCode);
        }

        try {
            return balance.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidAccountException("Le solde ne respecte pas la precision de " + currencyCode);
        }
    }
}
