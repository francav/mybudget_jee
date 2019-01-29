package br.com.victorpfranca.mybudget.account.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import br.com.victorpfranca.mybudget.accesscontroll.CredentialsStore;
import br.com.victorpfranca.mybudget.account.Account;
import br.com.victorpfranca.mybudget.account.AccountBalance;
import br.com.victorpfranca.mybudget.account.CheckingAccount;
import br.com.victorpfranca.mybudget.account.CreditCardAccount;
import br.com.victorpfranca.mybudget.period.PlanningPeriod;
import br.com.victorpfranca.mybudget.transaction.CheckingAccountTransaction;
import br.com.victorpfranca.mybudget.transaction.CreditCardTransaction;
import br.com.victorpfranca.mybudget.transaction.Transaction;
import br.com.victorpfranca.mybudget.transaction.extractors.saldofuturo.FutureAccountBalanceGenerator;
import br.com.victorpfranca.mybudget.transaction.rules.AccountTypeException;
import br.com.victorpfranca.mybudget.transaction.rules.DeletionNotPermittedException;
import br.com.victorpfranca.mybudget.transaction.rules.IncompatibleCategoriesException;
import br.com.victorpfranca.mybudget.transaction.rules.InvalidTransactionValueException;
import br.com.victorpfranca.mybudget.transaction.rules.NullableAccountException;
import br.com.victorpfranca.mybudget.transaction.rules.TransactionMonthUpdatedException;
import br.com.victorpfranca.mybudget.view.MonthYear;

@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class BankAccountService {

	@EJB
	private CheckingAccountBuilder checkingAccountBuilder;

	@EJB
	private CreditCardAccountBuilder creditCardAccountBuilder;

	@EJB
	private CheckingAccountRemover checkingAccountRemover;

	@EJB
	private CreditCardAccountRemover creditCardAccountRemover;

	@EJB
	private AccountBalanceFixer accountBalanceFixer;

	@EJB
	private PlanningPeriod planningPeriod;

	@EJB
	protected FutureAccountBalanceGenerator futureAccountBalanceGenerator;

	@Inject
	private EntityManager em;

	@EJB
	private CredentialsStore credentialsStore;

	public List<Account> findAll() {
		return em.createNamedQuery(Account.FIND_ALL_QUERY, Account.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado()).getResultList();
	}

	public Account find(Integer id) {
		return em.find(Account.class, id);
	}

	public List<Account> findContasCorrentes() {
		List<Account> contasCorrentes = new ArrayList<Account>();
		contasCorrentes.addAll(findContasBancos());
		contasCorrentes.addAll(findContasDinheiro());
		return contasCorrentes;
	}

	public List<Account> findContasBancos() {
		return em.createNamedQuery(Account.FIND_ALL_CONTA_BANCO_QUERY, Account.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado()).getResultList();
	}

	public List<Account> findContasDinheiro() {
		return em.createNamedQuery(Account.FIND_ALL_CONTA_DINHEIRO_QUERY, Account.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado()).getResultList();
	}

	public List<Account> findContasCartoes() {
		return em.createNamedQuery(Account.FIND_ALL_CONTA_CARTOES_QUERY, Account.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado()).getResultList();
	}

	public List<CreditCardTransaction> findLancamentosIniciaisCartao(CreditCardAccount conta) {
		return em.createNamedQuery(Transaction.FIND_LANCAMENTO_INICIAL_CARTAO_QUERY, CreditCardTransaction.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado()).setParameter("conta", conta)
				.getResultList();
	}

	public List<CheckingAccountTransaction> findFaturas(CreditCardAccount conta) {
		return em.createNamedQuery(Transaction.FIND_LANCAMENTO_CONTA_CORRENTE_QUERY, CheckingAccountTransaction.class)
				.setParameter("user", credentialsStore.recuperarIdUsuarioLogado())
				.setParameter("cartaoCreditoFatura", conta).setParameter("faturaCartao", true)
				.setParameter("saldoInicial", null).setParameter("ano", null).setParameter("mes", null)
				.setParameter("conta", null).setParameter("categoria", null).setParameter("status", null).getResultList();
	}

	public BigDecimal getSaldosContasCorrentesAte(Integer ano, Integer mes) {

		List<Account> accounts = findContasCorrentes();

		BigDecimal saldo = BigDecimal.ZERO;
		for (Iterator<Account> iterator = accounts.iterator(); iterator.hasNext();) {
			Account account = iterator.next();
			saldo = saldo.add(getSaldoAte(account, ano, mes));
		}

		return saldo;
	}

	public BigDecimal getSaldoAte(Integer conta, Integer ano, Integer mes) {
		return getSaldoAte(em.find(Account.class, conta), ano, mes);
	}

	public BigDecimal getSaldoAte(Account account, Integer ano, Integer mes) {
		List<AccountBalance> saldos = em.createNamedQuery(AccountBalance.FIND_UNTIL_ANO_MES_QUERY, AccountBalance.class)
				.setParameter("user", credentialsStore.recuperarUsuarioLogado()).setParameter("conta", account)
				.setParameter("ano", ano).setParameter("mes", mes).setMaxResults(1).getResultList();

		if (!saldos.isEmpty()) {
			return saldos.get(0).getValor();
		}

		return BigDecimal.ZERO;
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public List<Account> saveContasCorrente(List<Account> accounts)
			throws SameNameException, NullableAccountException, TransactionMonthUpdatedException,
			AccountTypeException, IncompatibleCategoriesException, InvalidTransactionValueException {
		List<Account> contasGravadas = new ArrayList<Account>();
		for (Iterator<Account> iterator = accounts.iterator(); iterator.hasNext();) {
			Account account = iterator.next();
			contasGravadas.add(saveContaCorrente(account));
		}
		return contasGravadas;
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public Account saveContaCorrente(Account account)
			throws SameNameException, NullableAccountException, TransactionMonthUpdatedException,
			AccountTypeException, IncompatibleCategoriesException, InvalidTransactionValueException {

		account = checkingAccountBuilder.save((CheckingAccount) account);

		return account;
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public void saveContasCartoes(List<Account> accounts)
			throws SameNameException, NullableAccountException, TransactionMonthUpdatedException,
			AccountTypeException, IncompatibleCategoriesException, InvalidTransactionValueException {
		for (Iterator<Account> iterator = accounts.iterator(); iterator.hasNext();) {
			Account account = iterator.next();
			saveContaCartao(account, new ArrayList<Transaction>());
		}
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public Account saveContaCartao(Account account, List<Transaction> transactions)
			throws SameNameException, NullableAccountException, TransactionMonthUpdatedException,
			AccountTypeException, IncompatibleCategoriesException, InvalidTransactionValueException {

		if (account instanceof CheckingAccount) {
			account = checkingAccountBuilder.save((CheckingAccount) account);
		} else if (account instanceof CreditCardAccount) {
			account = creditCardAccountBuilder.save((CreditCardAccount) account, transactions);
		}

		return account;
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public void remove(Account account) throws DeletionNotPermittedException, CantRemoveException {

		if (account instanceof CheckingAccount) {
			checkingAccountRemover.remover((CheckingAccount) account);
		} else if (account instanceof CreditCardAccount) {
			creditCardAccountRemover.remover((CreditCardAccount) account);
		}

	}

	public Map<Account, Map<MonthYear, AccountBalance>> reconstruirSaldosContas() {
		return accountBalanceFixer.reconstruirSaldosContasDoInicio();
	}

	public List<AccountBalance> carregarSaldoFuturoPrevisto() {
		MonthYear anoMesAtual = planningPeriod.getMesAtual();
		MonthYear anoMesFinal = planningPeriod.getMesFinal();

		return carregarSaldoFuturoPrevisto(anoMesAtual, anoMesFinal);
	}

	public List<AccountBalance> carregarSaldoFuturoPrevisto(MonthYear anoMesAtual, MonthYear anoMesFinal) {
		return futureAccountBalanceGenerator.execute(anoMesAtual.getAno(), anoMesAtual.getMes(), anoMesFinal.getAno(),
				anoMesFinal.getMes());
	}

}
