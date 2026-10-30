package ma.adria.transaction.service;
import java.util.UUID;
import java.math.BigDecimal;
import jakarta.validation.ConstraintViolationException;
import ma.adria.transaction.service.dto.*;
import ma.adria.transaction.service.exception.*;
import ma.adria.transaction.domain.BankTransaction;
import ma.adria.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties="spring.datasource.url=jdbc:h2:mem:transaction-business-tests;DB_CLOSE_DELAY=-1")
@Transactional
class TransactionBusinessTest {
    @Autowired TransactionPolicy policy;
    @Autowired TransactionQueryService queries;
    @Autowired TransactionRepository repository;
    final UUID id=UUID.randomUUID();
    CreateTransactionCommand command(String amount) {
        return new CreateTransactionCommand(id,new BigDecimal(amount),"EUR","ECOMMERCE","FR");
    }
    AccountSnapshot account(String status,String currency,String balance) {
        return new AccountSnapshot(id,status,currency,new BigDecimal(balance));
    }
    @Test void acceptsTheExactCeilingAndAnExactBalance() {
        assertThatCode(()->policy.validate(command("1000"),account("ACTIVE","EUR","1000"))).doesNotThrowAnyException();
    }
    @Test void rejectsAmountsAboveTheCeiling() {
        assertThatThrownBy(()->policy.validate(command("1000.01"),account("ACTIVE","EUR","5000")))
            .isInstanceOf(TransactionRejectedException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1"})
    void rejectsNonPositiveAmounts(String amount) {
        assertThatThrownBy(()->policy.validate(command(amount),account("ACTIVE","EUR","5000")))
            .isInstanceOf(ConstraintViolationException.class);
    }
    @Test void rejectsBlockedAccounts() {
        assertThatThrownBy(()->policy.validate(command("10"),account("BLOCKED","EUR","5000")))
            .isInstanceOf(TransactionRejectedException.class);
    }
    @Test void rejectsCurrencyMismatch() {
        assertThatThrownBy(()->policy.validate(command("10"),account("ACTIVE","MAD","5000")))
            .isInstanceOf(TransactionRejectedException.class);
    }
    @Test void rejectsInsufficientFunds() {
        assertThatThrownBy(()->policy.validate(command("10"),account("ACTIVE","EUR","9.99")))
            .isInstanceOf(TransactionRejectedException.class);
    }
    @Test void rejectsMissingOrUnrelatedAccounts() {
        assertThatThrownBy(()->policy.validate(command("10"),null)).isInstanceOf(TransactionRejectedException.class);
        var other=new AccountSnapshot(UUID.randomUUID(),"ACTIVE","EUR",new BigDecimal("5000"));
        assertThatThrownBy(()->policy.validate(command("10"),other)).isInstanceOf(TransactionRejectedException.class);
    }
    @Test void refusesSilentRounding() {
        assertThatThrownBy(()->policy.validate(command("0.001"),account("ACTIVE","EUR","5000")))
            .isInstanceOf(TransactionRejectedException.class);
    }
    @Test void readsOnlyTransactionsOfTheRequestedAccount() {
        var saved=repository.saveAndFlush(new BankTransaction(id,new BigDecimal("25"),"EUR","ECOMMERCE","FR"));
        repository.saveAndFlush(new BankTransaction(UUID.randomUUID(),new BigDecimal("10"),"EUR","ECOMMERCE","FR"));
        assertThat(queries.getTransaction(saved.getId()).amount()).isEqualByComparingTo("25");
        assertThat(queries.getTransactions(id,0,20).getTotalElements()).isEqualTo(1);
    }
    @Test void reportsUnknownTransactions() {
        assertThatThrownBy(()->queries.getTransaction(UUID.randomUUID())).isInstanceOf(TransactionNotFoundException.class);
    }
    @Test void rejectsUnboundedPagination() {
        assertThatThrownBy(()->queries.getTransactions(id,0,101)).isInstanceOf(ConstraintViolationException.class);
    }
}
