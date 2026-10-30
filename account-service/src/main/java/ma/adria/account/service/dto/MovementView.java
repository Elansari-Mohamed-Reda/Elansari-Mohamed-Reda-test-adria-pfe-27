package ma.adria.account.service.dto;
import java.util.UUID;
import java.math.BigDecimal;
public record MovementView(UUID operationId, UUID sourceAccountId, UUID targetAccountId,
 BigDecimal amount, String currency, MovementCommand.Type type,
 BigDecimal sourceBalanceAfter, BigDecimal targetBalanceAfter) {}
