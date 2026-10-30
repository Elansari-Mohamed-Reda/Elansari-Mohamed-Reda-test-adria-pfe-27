package ma.adria.transaction.client;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;
@Configuration
@EnableFeignClients(clients=AccountClient.class)
public class AccountClientConfiguration {}
