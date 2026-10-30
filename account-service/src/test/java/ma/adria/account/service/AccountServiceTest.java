package ma.adria.account.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import ma.adria.account.domain.AccountStatus;
import ma.adria.account.domain.BankAccount;
import ma.adria.account.repository.AccountRepository;
import ma.adria.account.service.dto.AccountPage;
import ma.adria.account.service.dto.AccountView;
import ma.adria.account.service.dto.ChangeAccountStatusCommand;
import ma.adria.account.service.dto.CreateAccountCommand;
import ma.adria.account.service.exception.AccountNotFoundException;
import ma.adria.account.service.exception.InvalidAccountException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:account-service-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsAnActiveAccountAndNormalizesTheCustomerReference() {
        AccountView created = service.createAccount(
                new CreateAccountCommand("  CLIENT-001  ", "EUR", new BigDecimal("1250.50")));
        entityManager.clear();

        BankAccount persisted = repository.findById(created.id()).orElseThrow();
        assertThat(created.id()).isNotNull();
        assertThat(created.customerReference()).isEqualTo("CLIENT-001");
        assertThat(persisted.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(persisted.getCurrency()).isEqualTo("EUR");
        assertThat(persisted.getBalance()).isEqualByComparingTo("1250.50");
    }

    @Test
    void allowsAZeroOpeningBalance() {
        AccountView account = service.createAccount(command("EUR", "0"));

        assertThat(account.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-500"})
    void rejectsNegativeBalancesAtTheServiceBoundary(String balance) {
        assertThatThrownBy(() -> service.createAccount(command("EUR", balance)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingCustomerReferences(String customerReference) {
        CreateAccountCommand command = new CreateAccountCommand(customerReference, "EUR", BigDecimal.ZERO);

        assertThatThrownBy(() -> service.createAccount(command))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsAMissingBalance() {
        assertThatThrownBy(() -> service.createAccount(new CreateAccountCommand("CLIENT", "EUR", null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsAMissingCreateCommand() {
        assertThatThrownBy(() -> service.createAccount(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "XXX"})
    void rejectsUnknownOrNonMonetaryCurrencyCodes(String currency) {
        assertThatThrownBy(() -> service.createAccount(command(currency, "100")))
                .isInstanceOf(InvalidAccountException.class);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"EUR, 0.001", "JPY, 0.1", "KWD, 0.0001"})
    void rejectsExtraDecimalPlacesWithoutRoundingTheBalance(String currency, String balance) {
        assertThatThrownBy(() -> service.createAccount(command(currency, balance)))
                .isInstanceOf(InvalidAccountException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void supportsCurrenciesWithThreeDecimalPlaces() {
        AccountView account = service.createAccount(command("KWD", "12.123"));
        entityManager.clear();

        assertThat(service.getAccount(account.id()).balance()).isEqualByComparingTo("12.123");
    }

    @Test
    void returnsAnExistingAccount() {
        AccountView created = service.createAccount(command("MAD", "150.25"));

        AccountView found = service.getAccount(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.currency()).isEqualTo("MAD");
        assertThat(found.balance()).isEqualByComparingTo("150.25");
    }

    @Test
    void throwsAnExplicitErrorForAnUnknownAccount() {
        assertThatThrownBy(() -> service.getAccount(UUID.randomUUID()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void returnsAccountsInBoundedPages() {
        service.createAccount(command("EUR", "10"));
        service.createAccount(command("EUR", "20"));
        service.createAccount(command("EUR", "30"));

        AccountPage first = service.getAccounts(0, 2);
        AccountPage second = service.getAccounts(1, 2);

        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.page()).isZero();
        assertThat(second.content()).hasSize(1);
        assertThat(second.page()).isEqualTo(1);
        assertThat(first.content()).extracting(AccountView::id)
                .doesNotContain(second.content().get(0).id());
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void rejectsInvalidPagination(int page, int size) {
        assertThatThrownBy(() -> service.getAccounts(page, size))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void blocksAndReactivatesAnAccountWithoutChangingItsBalance() {
        AccountView created = service.createAccount(command("EUR", "250"));

        AccountView blocked = service.changeStatus(created.id(),
                new ChangeAccountStatusCommand(AccountStatus.BLOCKED));
        entityManager.flush();
        entityManager.clear();
        assertThat(blocked.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(service.getAccount(created.id()).status()).isEqualTo(AccountStatus.BLOCKED);

        service.changeStatus(created.id(), new ChangeAccountStatusCommand(AccountStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
        AccountView reactivated = service.getAccount(created.id());
        assertThat(reactivated.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reactivated.balance()).isEqualByComparingTo("250");
        assertThat(reactivated.currency()).isEqualTo("EUR");
    }

    @Test
    void cannotChangeTheStatusOfAnUnknownAccount() {
        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(),
                new ChangeAccountStatusCommand(AccountStatus.BLOCKED)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejectsAMissingStatus() {
        AccountView created = service.createAccount(command("EUR", "100"));

        assertThatThrownBy(() -> service.changeStatus(created.id(), new ChangeAccountStatusCommand(null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(service.getAccount(created.id()).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsAMissingAccountId() {
        assertThatThrownBy(() -> service.getAccount(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private CreateAccountCommand command(String currency, String balance) {
        return new CreateAccountCommand("CLIENT-001", currency, new BigDecimal(balance));
    }
}
