package ma.adria.account.web.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:graphql-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AccountGraphqlTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void createsAndReadsAnExactDecimalAccount() throws Exception {
        String query = "mutation { createAccount(input:{customerReference:\"CLIENT-GQL\",currency:\"EUR\",balance:\"1250.50\"}) { id balance status } }";
        String response = mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query",query))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.createAccount.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String id=mapper.readTree(response).at("/data/createAccount/id").asText();
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query","{ account(id:\""+id+"\") { id currency } }"))))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.account.id").value(id));
    }

    @Test
    void returnsAnErrorForAnUnknownAccount() throws Exception {
        String query="{ account(id:\"00000000-0000-0000-0000-000000000001\") { id } }";
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query",query))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("NOT_FOUND"));
    }

    @Autowired ma.adria.account.service.AccountService service;

    @Test
    void changesStatusAndListsAccounts() throws Exception {
        var account = service.createAccount(new ma.adria.account.service.dto.CreateAccountCommand(
                "CLIENT-GQL", "EUR", new java.math.BigDecimal("250")));
        String mutation = "mutation($id:ID!){ changeAccountStatus(id:$id,status:BLOCKED){ status balance } }";
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query",mutation,"variables",Map.of("id",account.id().toString())))))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.changeAccountStatus.status").value("BLOCKED"));
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query","{ accounts { totalElements content { id status } } }"))))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.accounts.totalElements").value("1"))
                .andExpect(jsonPath("$.data.accounts.content[0].status").value("BLOCKED"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("query","{ accounts(page:-1) { page } }"))))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }
}
