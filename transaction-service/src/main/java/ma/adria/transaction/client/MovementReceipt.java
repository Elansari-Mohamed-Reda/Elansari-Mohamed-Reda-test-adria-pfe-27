package ma.adria.transaction.client;
import java.util.UUID;
import java.math.BigDecimal;
public record MovementReceipt(UUID operationId,UUID sourceAccountId,UUID targetAccountId,BigDecimal amount,String currency,String type) {}
