package ma.adria.transaction.repository;

import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.adria.transaction.domain.BankTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
class TransactionRepositoryTest {
    @Autowired TransactionRepository repository;
    @Autowired TestEntityManager entityManager;

    @Test
    void persistsTransactionWithExactAmountAndTimestamp() {
        UUID accountId=UUID.randomUUID();
        Instant before=Instant.now().minusSeconds(1);
        var saved=repository.saveAndFlush(transaction(accountId,"125.1234","FR"));
        entityManager.clear();
        var found=repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getAccountId()).isEqualTo(accountId);
        assertThat(found.getAmount()).isEqualByComparingTo("125.1234");
        assertThat(found.getCurrency()).isEqualTo("EUR");
        assertThat(found.getMerchantCategory()).isEqualTo("ECOMMERCE");
        assertThat(found.getCountry()).isEqualTo("FR");
        assertThat(found.getCreatedAt()).isBetween(before,Instant.now().plusSeconds(1));
    }

    @Test
    void filtersAndPaginatesTransactionsByAccount() {
        UUID first=UUID.randomUUID();
        UUID second=UUID.randomUUID();
        repository.saveAndFlush(transaction(first,"10","FR"));
        repository.saveAndFlush(transaction(first,"20","FR"));
        repository.saveAndFlush(transaction(second,"30","MA"));
        var page=repository.findByAccountId(first,PageRequest.of(0,1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAccountId()).isEqualTo(first);
        assertThat(repository.findByAccountId(UUID.randomUUID(),PageRequest.of(0,10))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings={"0","-0.01","-100"})
    void rejectsNonPositiveAmounts(String amount) {
        assertThatThrownBy(()->repository.saveAndFlush(transaction(UUID.randomUUID(),amount,"FR")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsAnAbsentAccountReference() {
        assertThatThrownBy(()->repository.saveAndFlush(transaction(null,"10","FR")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings={"France","fr"})
    void rejectsInvalidCountryFormats(String country) {
        assertThatThrownBy(()->repository.saveAndFlush(transaction(UUID.randomUUID(),"10",country)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private BankTransaction transaction(UUID accountId,String amount,String country) {
        return new BankTransaction(accountId,new BigDecimal(amount),"EUR","ECOMMERCE",country);
    }
}
