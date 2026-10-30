package ma.adria.gateway;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
@Configuration
public class BankRoutes {
 @Bean RouteLocator bankRouteLocator(RouteLocatorBuilder builder) {
  return builder.routes()
   .route("account-rest",r->r.path("/api/accounts","/api/accounts/**").uri("lb://account-service"))
   .route("transaction-rest",r->r.path("/api/transactions","/api/transactions/**").uri("lb://transaction-service"))
   .route("account-graphql",r->r.path("/accounts/graphql").filters(f->f.setPath("/graphql")).uri("lb://account-service"))
   .route("transaction-graphql",r->r.path("/transactions/graphql").filters(f->f.setPath("/graphql")).uri("lb://transaction-service"))
   .route("account-mcp",r->r.path("/accounts/mcp").filters(f->f.setPath("/mcp")).uri("lb://account-service"))
   .route("transaction-mcp",r->r.path("/transactions/mcp").filters(f->f.setPath("/mcp")).uri("lb://transaction-service"))
   .route("assistant",r->r.path("/api/chat","/api/chat/**").uri("lb://assistant-service"))
   .build();
 }
}
