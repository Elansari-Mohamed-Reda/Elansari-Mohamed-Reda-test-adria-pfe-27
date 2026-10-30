package ma.adria.account.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountCommand(
        @NotBlank @Size(max = 120) String customerReference,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal balance
) {
}
