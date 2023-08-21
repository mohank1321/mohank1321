package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
class AccountsControllerTest {

  private MockMvc mockMvc;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @BeforeEach
  void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }
  
  @Test
  public void testGetBalance() throws Exception {
      given(this.accountService.findById(1L))
              .willReturn(Optional.of(new Account(400)));
      this.mvc.perform(get("/balance/").accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":true,\"messages\":{},\"errors\":{},\"data\":{\"balance\":\"₹400.0\"},\"httpResponseCode\":200}"));
  }
  
  @Test
  public void testMaxDepositForTheDay() throws Exception {
      AccountTransaction transaction = new AccountTransaction(TransactionType.DEPOSIT.getId(), 100000, new Date());
      AccountTransaction transaction2 = new AccountTransaction(TransactionType.DEPOSIT.getId(), 40000, new Date());

      List<AccountTransaction> list = new ArrayList<>();
      list.add(transaction);
      list.add(transaction2);

      UserTransaction userTransaction = new UserTransaction(15000); // 3rd deposit ₹15K
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
              AccountUtils.getEndOfDay(new Date()), TransactionType.DEPOSIT.getId())).willReturn(list);
      this.mvc.perform(post("/deposit/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":false,\"messages\":{\"message\":\"Deposit for the day should not be more than ₹100K\",\"title\":\"Error\"},\"errors\":{},\"data\":{},\"httpResponseCode\":406}"));
  }
  
  @Test
  public void testWithdrawalExceedsCurrentBalance() throws Exception {

      UserTransaction userTransaction = new UserTransaction(50000);
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.accountService.findById(1L)).willReturn(Optional.of(new Account(40000)));

      this.mvc.perform(post("/withdrawal/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":false,\"messages\":{\"message\":\"You have insufficient funds\",\"title\":\"Error\"},\"errors\":{},\"data\":{},\"httpResponseCode\":406}"));

  }

  @Test
  public void testMaxWithdrawalForTheDay() throws Exception {

      AccountTransaction transaction = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 40000, new Date());
      AccountTransaction transaction2 = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 5000, new Date());

      List<AccountTransaction> list = new ArrayList<>();
      list.add(transaction);
      list.add(transaction2);

      UserTransaction userTransaction = new UserTransaction(8000);
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.accountService.findById(1L)).willReturn(Optional.of(new Account(400000)));

      given(this.transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
              AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId())).willReturn(list);

      this.mvc.perform(post("/withdrawal/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":false,\"messages\":{\"message\":\"Withdrawal per day should not be more than ₹50K\",\"title\":\"Error\"},\"errors\":{},\"data\":{},\"httpResponseCode\":406}"));

  }

  @Test
  public void testMaxWithdrawalPerTransaction() throws Exception {

      AccountTransaction transaction = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 5000, new Date());
      AccountTransaction transaction2 = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 7500, new Date());

      List<AccountTransaction> list = new ArrayList<>();
      list.add(transaction);
      list.add(transaction2);

      UserTransaction userTransaction = new UserTransaction(25000);
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.accountService.findById(1L)).willReturn(Optional.of(new Account(400000)));

      given(this.transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
              AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId())).willReturn(list);

      this.mvc.perform(post("/withdrawal/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":false,\"messages\":{\"message\":\"Exceeded Maximum Withdrawal Per Transaction\",\"title\":\"Error\"},\"errors\":{},\"data\":{},\"httpResponseCode\":406}"));

  }

  @Test
  public void testMaxAllowedWithdrawalPerDay() throws Exception {

      AccountTransaction transaction = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 5000, new Date());
      AccountTransaction transaction2 = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 7500, new Date());
      AccountTransaction transaction3 = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 10500, new Date());

      List<AccountTransaction> list = new ArrayList<>();
      list.add(transaction);
      list.add(transaction2);
      list.add(transaction3);

      UserTransaction userTransaction = new UserTransaction(1000);
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.accountService.findById(1L)).willReturn(Optional.of(new Account(400000)));

      given(this.transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
              AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId())).willReturn(list);

      this.mvc.perform(post("/withdrawal/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":false,\"messages\":{\"message\":\"Maximum Withdrawal transactions for the day Exceeded\",\"title\":\"Error\"},\"errors\":{},\"data\":{},\"httpResponseCode\":406}"));

  }

  @Test
  public void testSuccessfulWithdrawal() throws Exception {

      AccountTransaction transaction = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 5000, new Date());
      AccountTransaction transaction2 = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 7500, new Date());

      List<AccountTransaction> list = new ArrayList<>();
      list.add(transaction);
      list.add(transaction2);

      UserTransaction userTransaction = new UserTransaction(1000);
      Gson gson = new Gson();
      String json = gson.toJson(userTransaction);

      given(this.accountService.findById(1L)).willReturn(Optional.of(new Account(70000)));

      given(this.transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
              AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId())).willReturn(list);

      when(this.transactionsService.save(any(AccountTransaction.class))).thenReturn(transaction);
      when(this.accountService.save(any(Account.class))).thenReturn(new Account(400));

      this.mvc.perform(post("/withdrawal/").contentType(MediaType.APPLICATION_JSON).content(json))
              .andExpect(status().isOk()).andExpect(content().json("{\"success\":true,\"messages\":{\"message\":\"Withdrawal sucessfully Transacted\",\"title\":\"\"},\"errors\":{},\"data\":{},\"httpResponseCode\":200}"));

  }

  @Test
  void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
      .andExpect(status().isOk())
      .andExpect(
        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }
  
}
