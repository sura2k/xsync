package com.antkorwin.xsync;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.jupiter.tools.stress.test.concurrency.ExecutionMode;
import com.jupiter.tools.stress.test.concurrency.StressTestRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created on 27/12/2019
 * <p>
 * TODO: replace on the JavaDoc
 *
 * @author Korovin Anatoliy
 */
public class MultiKeysXSyncTest {

	private static final int ITERATIONS = 100_000;
	private static final int THREADS_COUNT = 8;
	private static final long INITIAL_BALANCE = 1000L;

	private XSync<Long> xsync = new XSync<>();


	@Test
	void multipleKeysExecute() {
		List<Account> accounts = LongStream.range(0, 10)
		                                   .boxed()
		                                   .map(i -> new Account(i, INITIAL_BALANCE))
		                                   .collect(Collectors.toList());

		// add two instance with the same id
		accounts.add(new Account(1L, INITIAL_BALANCE));

		StressTestRunner.test()
		                .mode(ExecutionMode.EXECUTOR_MODE)
		                .threads(THREADS_COUNT)
		                .iterations(ITERATIONS)
		                // deadlock prevention
		                .timeout(1, TimeUnit.MINUTES)
		                .run(() -> {
			                // select 3 different accounts
			                int fromId = randomExclude(accounts.size());
			                Account from = accounts.get(fromId);
			                int toId = randomExclude(accounts.size(), fromId);
			                Account to = accounts.get(toId);
			                int creditorId = randomExclude(accounts.size(), fromId, toId);
			                Account creditor = accounts.get(creditorId);
			                // Act
			                transfer(from, to, creditor);
		                });
		// Assert
		long sum = accounts.stream()
		                   .peek(System.out::println)
		                   .mapToLong(Account::getBalance)
		                   .sum();

		System.out.println("SUM=" + sum);
		assertThat(sum).isEqualTo(accounts.size() * INITIAL_BALANCE);
	}


	@Test
	void multipleKeysEvaluate() {
		List<Account> accounts = LongStream.range(0, 10)
		                                   .boxed()
		                                   .map(i -> new Account(i, INITIAL_BALANCE))
		                                   .collect(Collectors.toList());

		StressTestRunner.test()
		                .mode(ExecutionMode.EXECUTOR_MODE)
		                .threads(THREADS_COUNT)
		                .iterations(ITERATIONS)
		                // deadlock prevention
		                .timeout(1, TimeUnit.MINUTES)
		                .run(() -> {
			                int fromId = randomExclude(accounts.size());
			                Account from = accounts.get(fromId);
			                int toId = randomExclude(accounts.size(), fromId);
			                Account to = accounts.get(toId);
			                int creditorId = randomExclude(accounts.size(), fromId, toId);
			                Account creditor = accounts.get(creditorId);
			                // Act
			                long resultBalance = transferEval(from, to, creditor);
			                // Assert
			                assertThat(resultBalance).isGreaterThan(0);
		                });

		// Assert concurrency flow
		long sum = accounts.stream()
		                   .mapToLong(Account::getBalance)
		                   .sum();

		assertThat(sum).isEqualTo(accounts.size() * INITIAL_BALANCE);
	}


	private void transfer(Account first, Account second, Account collector) {
		xsync.execute(Arrays.asList(first.getId(), second.getId(), collector.getId()),
		              () -> {
			              collector.balance += first.balance / 2 + second.balance / 2;
			              first.balance -= first.balance / 2;
			              second.balance -= second.balance / 2;
		              });
	}

	private long transferEval(Account first, Account second, Account collector) {
		return xsync.evaluate(Arrays.asList(first.getId(), second.getId(), collector.getId()),
		                      () -> {
			                      collector.balance += first.balance / 2 + second.balance / 2;
			                      first.balance -= first.balance / 2;
			                      second.balance -= second.balance / 2;
			                      return collector.balance;
		                      });
	}

	private int randomExclude(int maxValue, Integer... excludingValue) {
		List<Integer> exclude = Arrays.asList(excludingValue);
		int rnd = new Random().nextInt(maxValue);
		while (exclude.contains(rnd)) {
			rnd = new Random().nextInt(maxValue);
		}
		return rnd;
	}

	class Account {
		private Long id;
		private long balance;

		public Account(Long id, long balance) {
			this.id = id;
			this.balance = balance;
		}

		public Long getId() {
			return id;
		}

		public long getBalance() {
			return balance;
		}

		@Override
		public String toString() {
			return "Account{" +
			       "id=" + id +
			       ", balance=" + balance +
			       '}';
		}
	}
}
