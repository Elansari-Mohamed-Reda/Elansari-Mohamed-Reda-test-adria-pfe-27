package ma.adria.transaction.client;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import ma.adria.transaction.service.TransactionPreparationService;
import ma.adria.transaction.service.dto.CreateTransactionCommand;
import ma.adria.transaction.service.exception.*;
import jakarta.validation.ConstraintViolationException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:communication-tests;DB_CLOSE_DELAY=-1",
 "spring.cloud.openfeign.client.config.account-service.readTimeout=1000",
 "spring.cloud.openfeign.client.config.account-service.connectTimeout=1000"})
class AccountCommunicationTest {
    static final UUID ID=UUID.randomUUID();
    static final AtomicInteger requests=new AtomicInteger();
    static volatile int status=200;
    static volatile String accountStatus="ACTIVE";
    static volatile long delay=0;
    static final ExecutorService executor=Executors.newCachedThreadPool();
    static final HttpServer server;
    static {
        try {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.setExecutor(executor);
            server.createContext("/api/accounts/",exchange->{
                requests.incrementAndGet();
                try {
                    if(delay>0) Thread.sleep(delay);
                    String body=String.format("{\"id\":\"%s\",\"status\":\"%s\",\"currency\":\"EUR\",\"balance\":500}",ID,accountStatus);
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(status,bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start();
        } catch(Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("bank.account-service.url",()->"http://127.0.0.1:"+server.getAddress().getPort());
    }
    @Autowired TransactionPreparationService service;
    @Autowired CircuitBreakerRegistry breakers;
    @BeforeEach void reset() {
        breakers.getAllCircuitBreakers().forEach(breaker->breaker.reset());
        requests.set(0);status=200;accountStatus="ACTIVE";delay=0;
    }
    @AfterAll static void stop() { server.stop(0);executor.shutdownNow(); }
    CreateTransactionCommand command(String amount) {
        return new CreateTransactionCommand(ID,new BigDecimal(amount),"EUR","ECOMMERCE","FR");
    }
    @Test void consultsTheAccountForEachValidation() {
        assertThat(service.validateAgainstCurrentAccount(command("10")).balance()).isEqualByComparingTo("500");
        service.validateAgainstCurrentAccount(command("20"));
        assertThat(requests.get()).isEqualTo(2);
    }
    @Test void ignores404ForCircuitHealthAndDoesNotInventAnAccount() {
        status=404;
        for(int i=0;i<4;i++) assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("10")))
            .isInstanceOf(TransactionRejectedException.class);
        status=200;
        assertThatCode(()->service.validateAgainstCurrentAccount(command("10"))).doesNotThrowAnyException();
        assertThat(requests.get()).isEqualTo(5);
    }
    @Test void opensTheCircuitAfterRepeatedServerFailures() {
        status=500;
        for(int i=0;i<3;i++) assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("10")))
            .isInstanceOf(AccountServiceUnavailableException.class);
        status=200;
        assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("10")))
            .isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(requests.get()).isEqualTo(3);
    }
    @Test void rejectsBadCommandsBeforeAnyNetworkCall() {
        assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("0")))
            .isInstanceOf(ConstraintViolationException.class);
        assertThat(requests.get()).isZero();
    }
    @Test void appliesBusinessRulesToTheRealHttpResponse() {
        accountStatus="BLOCKED";
        assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("10")))
            .isInstanceOf(TransactionRejectedException.class);
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void failsExplicitlyOnReadTimeout() {
        delay=1600;
        assertThatThrownBy(()->service.validateAgainstCurrentAccount(command("10")))
            .isInstanceOf(AccountServiceUnavailableException.class);
    }
}
