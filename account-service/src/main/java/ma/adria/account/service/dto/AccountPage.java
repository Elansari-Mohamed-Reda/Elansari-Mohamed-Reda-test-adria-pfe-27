package ma.adria.account.service.dto;

import java.util.List;

public record AccountPage(
        List<AccountView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AccountPage {
        content = List.copyOf(content);
    }
}
