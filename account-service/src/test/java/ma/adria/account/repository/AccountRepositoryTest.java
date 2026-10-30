package ma.adria.account.repository;

import jakarta.validation.ConstraintViolationException;
import ma.adria.account.domain.AccountStatus;
import ma.adria.account.domain.BankAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AccountRepositoryTest {

    @Autowired
    private AccountRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndReloadsAnAccountWithoutLosingDecimalPrecision() {
        BankAccount account = repository.saveAndFlush(
                new BankAccount("CLIENT-001", "EUR", new BigDecimal("1234.56")));
        assertThat(account.getId()).isNotNull();
        entityManager.clear();

        BankAccount reloaded = repository.findById(account.getId()).orElseThrow();

        assertThat(reloaded.getCustomerReference()).isEqualTo("CLIENT-001");
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.getCurrency()).isEqualTo("EUR");
        assertThat(reloaded.getBalance()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getVersion()).isNotNull();
    }

    @Test
    void allowsSeveralAccountsForTheSameCustomer() {
        BankAccount first = repository.saveAndFlush(
                new BankAccount("CLIENT-001", "EUR", BigDecimal.ZERO));
        BankAccount second = repository.saveAndFlush(
                new BankAccount("CLIENT-001", "MAD", new BigDecimal("50.00")));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void rejectsANegativeOpeningBalance() {
        BankAccount account = new BankAccount("CLIENT-001", "EUR", new BigDecimal("-0.01"));

        assertThatThrownBy(() -> repository.saveAndFlush(account))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankCustomerReference(String customerReference) {
        BankAccount account = new BankAccount(customerReference, "EUR", BigDecimal.ZERO);

        assertThatThrownBy(() -> repository.saveAndFlush(account))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
