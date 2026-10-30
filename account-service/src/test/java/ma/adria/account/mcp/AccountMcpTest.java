package ma.adria.account.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ma.adria.account.repository.AccountRepository;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.datasource.url=jdbc:h2:mem:mcp-tests;DB_CLOSE_DELAY=-1")
class AccountMcpTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository repository;
    private final HttpClient client=HttpClient.newHttpClient();

    @BeforeEach
    void clearAccounts() { repository.deleteAll(); }

    private JsonNode rpc(String method, Map<String,Object> params) throws Exception {
        String body=mapper.writeValueAsString(Map.of("jsonrpc","2.0","id",1,"method",method,"params",params));
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp"))
                .header("Content-Type","application/json")
                .header("Accept","application/json, text/event-stream")
                .header("MCP-Protocol-Version","2025-06-18")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String payload=response.body();
        if(payload.startsWith("event:") || payload.startsWith("data:")) {
            payload=payload.lines().filter(line->line.startsWith("data:"))
                    .map(line->line.substring(5).strip()).findFirst().orElseThrow();
        }
        return mapper.readTree(payload);
    }

    private JsonNode call(String name,Map<String,Object> args) throws Exception {
        return rpc("tools/call",Map.of("name",name,"arguments",args));
    }

    @Test
    void discoversTheFourAccountTools() throws Exception {
        var result=rpc("tools/list",Map.of());
        assertThat(result.at("/result/tools").size()).isEqualTo(4);
        assertThat(result.toString()).contains("account_get","account_list","account_create","account_change_status");
    }

    @Test
    void createsReadsBlocksAndListsThroughMcp() throws Exception {
        var created=call("account_create",Map.of("customerReference","CLIENT-MCP","currency","EUR","balance","100.25"));
        assertThat(created.at("/result/isError").asBoolean()).isFalse();
        assertThat(repository.count()).isEqualTo(1);
        String id=repository.findAll().get(0).getId().toString();
        assertThat(call("account_get",Map.of("accountId",id)).toString()).contains("100.25");
        assertThat(call("account_change_status",Map.of("accountId",id,"status","BLOCKED")).toString()).contains("BLOCKED");
        assertThat(call("account_list",Map.of("page",0,"size",20)).toString()).contains(id);
    }

    @Test
    void rejectsInvalidBalancesWithoutPersistingAnAccount() throws Exception {
        var result=call("account_create",Map.of("customerReference","CLIENT-MCP","currency","EUR","balance","-1"));
        assertThat(result.at("/result/isError").asBoolean()).isTrue();
        assertThat(repository.count()).isZero();
    }
}
