package ma.adria.account.service.dto;

import jakarta.validation.constraints.NotNull;
import ma.adria.account.domain.AccountStatus;

public record ChangeAccountStatusCommand(@NotNull AccountStatus status) {
}
