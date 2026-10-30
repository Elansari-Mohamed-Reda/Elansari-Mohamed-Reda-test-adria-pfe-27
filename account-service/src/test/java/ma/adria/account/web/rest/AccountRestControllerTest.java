package ma.adria.account.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.adria.account.repository.AccountRepository;
import ma.adria.account.service.AccountService;
import ma.adria.account.service.dto.CreateAccountCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-rest-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
@Transactional
class AccountRestControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository repository;

    @Test
    void createsAnAccountAndReturnsItsLocation() throws Exception {
        MvcResult result = mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"CLIENT-001","currency":"EUR","balance":1250.50}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(1250.5))
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/accounts/" + id);
        mvc.perform(get(result.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerReference").value("CLIENT-001"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void rejectsANegativeBalanceWithAFieldError() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"CLIENT-001","currency":"EUR","balance":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.balance").exists());
        assertThat(repository.count()).isZero();
    }

    @Test
    void returnsABusinessErrorForAnUnknownCurrency() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"CLIENT-001","currency":"ZZZ","balance":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Compte invalide"))
                .andExpect(jsonPath("$.detail").value("Devise ISO 4217 inconnue : ZZZ"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsAnAmountThatWouldRequireRounding() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerReference":"CLIENT-001","currency":"EUR","balance":0.001}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Le solde ne respecte pas la precision de EUR"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void reportsMissingMandatoryFields() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerReference").exists())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.balance").exists());
    }

    @Test
    void rejectsMalformedJsonWithoutExposingAStackTrace() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corps JSON invalide"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rejectsAnAbsentRequestBody() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corps JSON invalide"));
    }

    @Test
    void returns404ForAnUnknownAccount() throws Exception {
        mvc.perform(get("/api/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Compte introuvable"));
    }

    @Test
    void rejectsAnInvalidUuid() throws Exception {
        mvc.perform(get("/api/accounts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametre invalide"));
    }

    @Test
    void listsAccountsUsingDefaultPagination() throws Exception {
        createAccount();
        createAccount();

        mvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void appliesRequestedPagination() throws Exception {
        createAccount();
        createAccount();
        createAccount();

        mvc.perform(get("/api/accounts").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void mapsServicePaginationValidationTo400(String page, String size) throws Exception {
        mvc.perform(get("/api/accounts").param("page", page).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void rejectsANonNumericPage() throws Exception {
        mvc.perform(get("/api/accounts").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametre invalide"));
    }

    @Test
    void blocksAnAccountWithoutChangingItsBalance() throws Exception {
        UUID id = createAccount();

        mvc.perform(patch("/api/accounts/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.balance").value(100));
        mvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void rejectsAnUnknownStatusValue() throws Exception {
        mvc.perform(patch("/api/accounts/{id}/status", createAccount())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corps JSON invalide"));
    }

    @Test
    void rejectsAMissingStatusValue() throws Exception {
        mvc.perform(patch("/api/accounts/{id}/status", createAccount())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void returns404WhenChangingAnUnknownAccount() throws Exception {
        mvc.perform(patch("/api/accounts/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isNotFound());
    }

    private UUID createAccount() {
        return service.createAccount(new CreateAccountCommand("CLIENT-001", "EUR", new BigDecimal("100"))).id();
    }
}
