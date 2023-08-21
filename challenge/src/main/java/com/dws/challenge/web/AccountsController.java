package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.service.AccountsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/accounts")
@Slf4j
public class AccountsController {

  private final AccountsService accountsService;
  
  // Deposit limits
  private static final double MAX_DEPOSIT_PER_TRANSACTION = 20000; // ₹20k
  private static final double MAX_DEPOSIT_PER_DAY = 100000; // ₹100k
  private static final int MAX_DEPOSIT_TRANSACTIONS_PER_DAY = 10;
  
  // Withdrawal limits
  private static final double MAX_WITHDRAWAL_PER_TRANSACTION = 20000; // ₹20k
  private static final double MAX_WITHDRAWAL_PER_DAY = 50000; // ₹50k
  private static final int MAX_WITHDRAWAL_TRANSACTIONS_PER_DAY = 3;

  @Autowired
  public AccountsController(AccountsService accountsService) {
    this.accountsService = accountsService;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createAccount(@RequestBody @Valid Account account) {
    log.info("Creating account {}", account);

    try {
    this.accountsService.createAccount(account);
    } catch (DuplicateAccountIdException daie) {
      return new ResponseEntity<>(daie.getMessage(), HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @GetMapping(path = "/{accountId}")
  public Account getAccount(@PathVariable String accountId) {
    log.info("Retrieving account for id {}", accountId);
    return this.accountsService.getAccount(accountId);
  }
  
  /////
  
  @GetMapping(path = "/{accountId}/balance")
  public Account getAccount(@PathVariable String accountId) {
	  log.info("Retrieving the balance for id {}", accountId);
	  StandardJsonResponse getBalance() {
	
	      StandardJsonResponse jsonResponse = new StandardJsonResponseImpl();
	      HashMap<String, Object> responseData = new HashMap<>();
	
	      try {
	          Optional<Account> account = Optional.of(accountService.findById(ACCOUNT_ID).get());
	
	          if (account.isPresent()) {
	              responseData.put("balance", "₹" + account.get().getAmount());
	
	              jsonResponse.setSuccess(true);
	              jsonResponse.setData(responseData);
	              jsonResponse.setHttpResponseCode(HttpStatus.SC_OK);
	          } else {
	              jsonResponse.setSuccess(false, "Resource not found", StandardJsonResponse.RESOURCE_NOT_FOUND_MSG);
	              jsonResponse.setHttpResponseCode(HttpStatus.SC_NO_CONTENT);
	          }
	
	      } catch (Exception e) {
	          logger.error("exception", e);
	          jsonResponse.setSuccess(false, StandardJsonResponse.DEFAULT_MSG_TITLE_VALUE, StandardJsonResponse.DEFAULT_MSG_NAME_VALUE);
	          jsonResponse.setHttpResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
	          return jsonResponse;
	      }
	
	      return jsonResponse;
	  }
  }
  /////
  
  //////
  @PostMapping("/{accountId}/deposit")
  public @ResponseBody StandardJsonResponse makeDeposit(@RequestBody UserTransaction userTransaction) {

      StandardJsonResponse jsonResponse = new StandardJsonResponseImpl();

      try {

          double total = 0;

          // check maximum limit deposit for the day has been reached
          List<AccountTransaction> deposits = transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
                  AccountUtils.getEndOfDay(new Date()), TransactionType.DEPOSIT.getId());

          if (deposits.size() > 0) {
              for (AccountTransaction accountTransaction : deposits) {
                  total += accountTransaction.getAmount();
              }
              if (total + userTransaction.getAmount() > MAX_DEPOSIT_PER_DAY) {
                  jsonResponse.setSuccess(false, "Error", "Deposit for the day should not be more than ₹ 100K");
                  jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
                  return jsonResponse;
              }
          }

          // Check whether the amount being deposited exceeds the MAX_DEPOSIT_PER_TRANSACTION
          if (userTransaction.getAmount() > MAX_DEPOSIT_PER_TRANSACTION) {
              jsonResponse.setSuccess(false, "Error", "Deposit per transaction should not be more than ₹ 20K");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
              return jsonResponse;
          }

          // check whether transactions exceeds the max allowed per day
          if (deposits.size() < MAX_DEPOSIT_TRANSACTIONS_PER_DAY) {
              AccountTransaction accountTransaction = new AccountTransaction(TransactionType.DEPOSIT.getId(), userTransaction.getAmount(), new Date());
              double amount = transactionsService.save(accountTransaction).getAmount();

              Optional<Account> account = accountService.findById(ACCOUNT_ID);
              double newBalance = account.get().getAmount() + amount;
              account.get().setAmount(newBalance);

              Account account1 = account.get();
              accountService.save(account1);

              jsonResponse.setSuccess(true, "", "Deposit sucessfully Transacted");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_OK);

          } else {
              jsonResponse.setSuccess(false, "Error", "maximum transactions for the day Exceeded");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
          }

      } catch (Exception e) {
          logger.error("exception", e);
          jsonResponse.setSuccess(false, StandardJsonResponse.DEFAULT_MSG_TITLE_VALUE, StandardJsonResponse.DEFAULT_MSG_NAME_VALUE);
          jsonResponse.setHttpResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
          return jsonResponse;
      }

      return jsonResponse;
  }
  
  //////
  
  /////
  
  @PostMapping("/{accountId}/withdrawal")
  public @ResponseBody
  StandardJsonResponse makeWithDrawal(@RequestBody UserTransaction userTransaction) {

      StandardJsonResponse jsonResponse = new StandardJsonResponseImpl();

      try {

          double total = 0;

          // check balance
          double balance = accountService.findById(ACCOUNT_ID).get().getAmount();
          if (userTransaction.getAmount() > balance) {
              jsonResponse.setSuccess(false, "Error", "You have insufficient funds");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
              return jsonResponse;
          }


          // check maximum limit withdrawal for the day has been reached
          List<AccountTransaction> withdrawals = transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()),
                  AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId());

          if (withdrawals.size() > 0) {
              for (AccountTransaction accountTransaction : withdrawals) {
                  total += accountTransaction.getAmount();
              }
              if (total + userTransaction.getAmount() > MAX_WITHDRAWAL_PER_DAY) {
                  jsonResponse.setSuccess(false, "Error", "Withdrawal per day should not be more than ₹50K");
                  jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
                  return jsonResponse;
              }
          }

          // Check whether the amount being withdrawn exceeds the MAX_WITHDRAWAL_PER_TRANSACTION
          if (userTransaction.getAmount() > MAX_WITHDRAWAL_PER_TRANSACTION) {
              jsonResponse.setSuccess(false, "Error", "Exceeded Maximum Withdrawal Per Transaction");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
              return jsonResponse;
          }

          // check whether transactions exceeds the max allowed per day
          if (withdrawals.size() < MAX_WITHDRAWAL_TRANSACTIONS_PER_DAY) {
              AccountTransaction accountTransaction = new AccountTransaction(TransactionType.WITHDRAWAL.getId(), userTransaction.getAmount(), new Date());
              double amount = transactionsService.save(accountTransaction).getAmount();

              Account account = accountService.findById(ACCOUNT_ID).get();
              double newBalance = account.getAmount() - amount;
              account.setAmount(newBalance);
              accountService.save(account);

              jsonResponse.setSuccess(true, "", "Withdrawal sucessfully Transacted");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_OK);

          } else {
              jsonResponse.setSuccess(false, "Error", "Maximum Withdrawal transactions for the day Exceeded");
              jsonResponse.setHttpResponseCode(HttpStatus.SC_NOT_ACCEPTABLE);
          }

      } catch (Exception e) {
          logger.error("exception", e);
          jsonResponse.setSuccess(false, StandardJsonResponse.DEFAULT_MSG_TITLE_VALUE, StandardJsonResponse.DEFAULT_MSG_NAME_VALUE);
          jsonResponse.setHttpResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
          return jsonResponse;
      }

      return jsonResponse;
  }
  
  /////

}
