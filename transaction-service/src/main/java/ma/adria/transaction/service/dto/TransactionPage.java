package ma.adria.transaction.service.dto;
import java.util.List;
import org.springframework.data.domain.Page;
public record TransactionPage(List<TransactionView> content,int page,int size,long totalElements,int totalPages) {
 public TransactionPage { content=List.copyOf(content); }
 public static TransactionPage from(Page<TransactionView> page) {
  return new TransactionPage(page.getContent(),page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages());
 }
}
