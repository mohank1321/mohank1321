package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;

import java.util.Date;
import java.util.List;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@RunWith(SpringRunner.class)
@DataJpaTest
class TransactionsServiceTests {

    @Autowired
    TransactionsService transactionsService;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    public void testFindByDateBetweenAndType() {
        this.entityManager.persist(new AccountTransaction(TransactionType.WITHDRAWAL.getId(), 1000, new Date()));
        List<AccountTransaction> transactions = transactionsService.findByDateBetweenAndType(AccountUtils.getStartOfDay(new Date()), AccountUtils.getEndOfDay(new Date()), TransactionType.WITHDRAWAL.getId());

    }

}
