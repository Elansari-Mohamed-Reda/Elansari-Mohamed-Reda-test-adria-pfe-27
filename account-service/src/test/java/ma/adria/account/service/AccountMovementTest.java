package ma.adria.account.service;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import ma.adria.account.domain.AccountStatus;
import ma.adria.account.repository.AccountRepository;
import ma.adria.account.service.dto.*;
import ma.adria.account.service.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties="spring.datasource.url=jdbc:h2:mem:movement-tests;DB_CLOSE_DELAY=-1")
class AccountMovementTest {
 @Autowired AccountMovementService movements;
 @Autowired AccountService accounts;
 @Autowired AccountRepository repository;
 @Autowired JdbcTemplate jdbc;
 @BeforeEach void clear() { jdbc.update("delete from bank_movements");repository.deleteAll(); }
 UUID account(String balance) { return accounts.createAccount(new CreateAccountCommand("CLIENT","EUR",new BigDecimal(balance))).id(); }
 MovementCommand command(UUID operation,MovementCommand.Type type,UUID source,UUID target,String amount) {
  return new MovementCommand(operation,type,source,target,new BigDecimal(amount),"EUR");
 }
 @Test void transfersBothBalancesAndReplaysWithoutMovingMoneyAgain() {
  UUID source=account("100"),target=account("20");
  var command=command(UUID.randomUUID(),MovementCommand.Type.TRANSFER,source,target,"30");
  movements.apply(command);movements.apply(command);
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("70");
  assertThat(accounts.getAccount(target).balance()).isEqualByComparingTo("50");
  assertThat(movements.get(command.operationId()).amount()).isEqualByComparingTo("30");
 }
 @Test void refusesReusingAnOperationIdForAnotherAmount() {
  UUID source=account("100"),operation=UUID.randomUUID();
  movements.apply(command(operation,MovementCommand.Type.DEBIT,source,null,"10"));
  assertThatThrownBy(()->movements.apply(command(operation,MovementCommand.Type.DEBIT,source,null,"20")))
   .isInstanceOf(MovementConflictException.class);
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("90");
 }
 @Test void neverDebitsIfTheTargetIsBlocked() {
  UUID source=account("100"),target=account("20");
  accounts.changeStatus(target,new ChangeAccountStatusCommand(AccountStatus.BLOCKED));
  assertThatThrownBy(()->movements.apply(command(UUID.randomUUID(),MovementCommand.Type.TRANSFER,source,target,"30")))
   .isInstanceOf(InvalidAccountException.class);
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("100");
  assertThat(accounts.getAccount(target).balance()).isEqualByComparingTo("20");
 }
 @Test void supportsCreditsAndRejectsOverdrafts() {
  UUID source=account("10");
  movements.apply(command(UUID.randomUUID(),MovementCommand.Type.CREDIT,null,source,"5"));
  assertThatThrownBy(()->movements.apply(command(UUID.randomUUID(),MovementCommand.Type.DEBIT,source,null,"16")))
   .isInstanceOf(InvalidAccountException.class);
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("15");
 }
 @Test void serializesConcurrentDuplicateRequests() throws Exception {
  UUID source=account("100");
  var command=command(UUID.randomUUID(),MovementCommand.Type.DEBIT,source,null,"10");
  var executor=Executors.newFixedThreadPool(4);
  try {
   var futures=new ArrayList<Future<MovementView>>();
   for(int i=0;i<4;i++)futures.add(executor.submit(()->movements.apply(command)));
   for(var future:futures)future.get(10,TimeUnit.SECONDS);
  } finally { executor.shutdownNow(); }
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("90");
 }
 @Test void preventsConcurrentOverdrafts() throws Exception {
  UUID source=account("100");
  var executor=Executors.newFixedThreadPool(2);
  try {
   Callable<Boolean> debit=()->{
    try { movements.apply(command(UUID.randomUUID(),MovementCommand.Type.DEBIT,source,null,"80"));return true; }
    catch(InvalidAccountException refused) { return false; }
   };
   var first=executor.submit(debit);var second=executor.submit(debit);
   assertThat((first.get(10,TimeUnit.SECONDS)?1:0)+(second.get(10,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
  } finally { executor.shutdownNow(); }
  assertThat(accounts.getAccount(source).balance()).isEqualByComparingTo("20");
 }
}
