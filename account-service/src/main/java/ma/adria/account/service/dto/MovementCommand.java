package ma.adria.account.service.dto;
import java.util.UUID;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record MovementCommand(@NotNull UUID operationId, @NotNull Type type,
 UUID sourceAccountId, UUID targetAccountId,
 @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal amount,
 @NotBlank @Pattern(regexp="[A-Z]{3}") String currency) {
 public enum Type { DEBIT, CREDIT, TRANSFER }
 public String fingerprint() {
  return type+"|"+sourceAccountId+"|"+targetAccountId+"|"+amount.stripTrailingZeros().toPlainString()+"|"+currency;
 }
}
