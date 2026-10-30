package ma.adria.transaction.service.dto;
import java.util.UUID;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record SubmitTransactionCommand(@NotNull UUID requestId,@NotNull UUID accountId,UUID targetAccountId,
 @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal amount,
 @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
 @NotBlank @Size(max=64) String merchantCategory,@NotBlank @Pattern(regexp="[A-Z]{2}") String country) {
 public CreateTransactionCommand basic() { return new CreateTransactionCommand(accountId,amount,currency,merchantCategory,country); }
}
