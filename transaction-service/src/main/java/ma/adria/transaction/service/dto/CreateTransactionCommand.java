package ma.adria.transaction.service.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record CreateTransactionCommand(
    @NotNull UUID accountId,
    @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal amount,
    @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
    @NotBlank @Size(max=64) String merchantCategory,
    @NotBlank @Pattern(regexp="[A-Z]{2}") String country) {}
