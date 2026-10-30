package ma.adria.transaction.mcp;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.*;
@Configuration
public class McpToolConfiguration {
 @Bean ToolCallbackProvider transactionToolCallbacks(TransactionMcpTools tools) {
  return MethodToolCallbackProvider.builder().toolObjects(tools).build();
 }
}
