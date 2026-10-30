package ma.adria.transaction.client;
import java.util.UUID;
import java.math.BigDecimal;
public record MovementRequest(UUID operationId,String type,UUID sourceAccountId,UUID targetAccountId,BigDecimal amount,String currency) {}
