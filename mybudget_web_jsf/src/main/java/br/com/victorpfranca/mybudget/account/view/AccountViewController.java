package br.com.victorpfranca.mybudget.account.view;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import br.com.victorpfranca.mybudget.InOut;
import br.com.victorpfranca.mybudget.LocalDateConverter;
import br.com.victorpfranca.mybudget.account.Account;
import br.com.victorpfranca.mybudget.account.BankAccount;
import br.com.victorpfranca.mybudget.account.CreditCardAccount;
import br.com.victorpfranca.mybudget.account.MoneyAccount;
import br.com.victorpfranca.mybudget.account.rules.BankAccountService;
import br.com.victorpfranca.mybudget.account.rules.CantRemoveException;
import br.com.victorpfranca.mybudget.account.rules.SameNameException;
import br.com.victorpfranca.mybudget.period.PlanningPeriod;
import br.com.victorpfranca.mybudget.transaction.CreditCardTransaction;
import br.com.victorpfranca.mybudget.transaction.Transaction;
import br.com.victorpfranca.mybudget.transaction.TransactionStatus;
import br.com.victorpfranca.mybudget.transaction.rules.AccountTypeException;
import br.com.victorpfranca.mybudget.transaction.rules.DeletionNotPermittedException;
import br.com.victorpfranca.mybudget.transaction.rules.IncompatibleCategoriesException;
import br.com.victorpfranca.mybudget.transaction.rules.InvalidTransactionValueException;
import br.com.victorpfranca.mybudget.transaction.rules.NullableAccountException;
import br.com.victorpfranca.mybudget.transaction.rules.TransactionMonthUpdatedException;
import br.com.victorpfranca.mybudget.view.FacesMessages;
import br.com.victorpfranca.mybudget.view.Messages;
import br.com.victorpfranca.mybudget.view.MonthYear;

@Named
@ViewScoped
public class AccountViewController implements Serializable {

	private static final long serialVersionUID = 1L;

	@Inject
	private BankAccountService bankAccountService;

	@Inject
	private PlanningPeriod planningPeriod;

	private int selectedTab;
	private Account objeto;
	private boolean telaGrid = true;

	private Map<Date, BigDecimal> faturasPreview;

	@PostConstruct
	public void init() {
		setSelectedTab(0);
	}

	public void incluirContaBanco() {
		setSelectedTab(0);
		setTelaGrid(false);
		Account account = new BankAccount();
		setObjeto(account);
	}

	public void incluirContaDinheiro() {
		setSelectedTab(0);
		setTelaGrid(false);
		Account account = new MoneyAccount();
		setObjeto(account);
	}

	public void incluirContaCartao() {
		setSelectedTab(0);
		setTelaGrid(false);
		Account account = new CreditCardAccount();
		setObjeto(account);
		this.faturasPreview = carregarFaturasPreview();
	}

	public void alterar(Account account) {
		setSelectedTab(0);
		setTelaGrid(false);
		setObjeto(account);

		if (account instanceof CreditCardAccount) {
			Map<Date, BigDecimal> faturasPreview = carregarFaturasPreview();
			this.faturasPreview = carregarFaturasCartao(getObjeto(), faturasPreview);
		}
	}

	private Map<Date, BigDecimal> carregarFaturasCartao(Account account, Map<Date, BigDecimal> faturasPreview) {

		Calendar cal = Calendar.getInstance();

		List<CreditCardTransaction> lancamentosCartaoExistentes = bankAccountService
				.findLancamentosIniciaisCartao((CreditCardAccount) getObjeto());
		for (Iterator<CreditCardTransaction> iterator = lancamentosCartaoExistentes.iterator(); iterator.hasNext();) {
			Transaction faturaExistente = (Transaction) iterator.next();

			for (Iterator<Date> iterator2 = faturasPreview.keySet().iterator(); iterator2.hasNext();) {
				Date faturaPreviewDate = (Date) iterator2.next();
				cal.setTime(faturaPreviewDate);
				int mes = cal.get(Calendar.MONTH) + 1;
				int ano = cal.get(Calendar.YEAR);
				if ((ano == faturaExistente.getAno().intValue()) && (mes == faturaExistente.getMes().intValue())) {
					faturasPreview.put(faturaPreviewDate, faturaExistente.getValor());
				}
			}
		}

		return faturasPreview;
	}

	private Map<Date, BigDecimal> carregarFaturasPreview() {
		Map<Date, BigDecimal> faturasParaView = new LinkedHashMap<Date, BigDecimal>();

		MonthYear anoMesInicio = planningPeriod.getMesInicio();
		MonthYear anoMesFinal = planningPeriod.getMesFinal();

		while (anoMesInicio.compareTo(anoMesFinal) <= 0) {
			LocalDate localDate = LocalDate.of(anoMesInicio.getAno(), anoMesInicio.getMes(), 1);
			faturasParaView.put(LocalDateConverter.toDate(localDate), null);
			anoMesInicio = anoMesInicio.plusMonths(1);
		}

		return faturasParaView;
	}

	public void voltar() {
		setTelaGrid(true);
		setSelectedTab(0);
		setObjeto(null);
	}

	public boolean isTelaGrid() {
		return telaGrid;
	}

	public void setTelaGrid(boolean telaGrid) {
		this.telaGrid = telaGrid;
	}

	public Account getObjeto() {
		return objeto;
	}

	public void setObjeto(Account objeto) {
		this.objeto = objeto;
	}

	public void excluir(Account account) {
		try {
			bankAccountService.remove(account);
		} catch (DeletionNotPermittedException e) {
			FacesMessages.fatal(Messages.msg(e.getMessage()));
		} catch (CantRemoveException e) {
			FacesMessages.fatal(Messages.msg(e.getMessage(), e.getAccountCartao()));
		}
	}

	public void salvar() throws NullableAccountException {

		Account account = getObjeto();

		try {
			if (account instanceof CreditCardAccount) {
				List<Transaction> lancamentosCartao = criarLancamentosCartao((CreditCardAccount) account);
				setObjeto(bankAccountService.saveContaCartao(account, lancamentosCartao));
			} else {
				setObjeto(bankAccountService.saveContaCorrente(account));
			}
		} catch (SameNameException | TransactionMonthUpdatedException | AccountTypeException
				| IncompatibleCategoriesException | InvalidTransactionValueException e) {
			FacesMessages.fatal(Messages.msg(e.getMessage()));
			return;
		}

		voltar();
	}

	private List<Transaction> criarLancamentosCartao(CreditCardAccount conta) throws NullableAccountException {
		List<Transaction> lancamentosCartao = new ArrayList<Transaction>();
		for (Iterator<Date> iterator = faturasPreview.keySet().iterator(); iterator.hasNext();) {
			Date faturaDate = (Date) iterator.next();
			if (faturasPreview.get(faturaDate) != null) {
				BigDecimal valor = new BigDecimal(String.valueOf(faturasPreview.get(faturaDate)));

				Date date = LocalDateConverter
						.toDate(LocalDateConverter.fromDate(faturaDate).withDayOfMonth(conta.getCartaoDiaPagamento()));

				CreditCardTransaction lancamentoCartao = new CreditCardTransaction();
				lancamentoCartao.setSaldoInicial(true);
				lancamentoCartao.setConta(conta);
				lancamentoCartao.setData(date);
				lancamentoCartao.setValor(valor);
				lancamentoCartao.setQtdParcelas(1);
				lancamentoCartao.setInOut(InOut.S);
				lancamentoCartao.setStatus(TransactionStatus.NAO_CONFIRMADO);

				lancamentosCartao.add(lancamentoCartao);
			}
		}
		return lancamentosCartao;
	}

	public int getSelectedTab() {
		return selectedTab;
	}

	public void setSelectedTab(int selectedTab) {
		this.selectedTab = selectedTab;
	}

	public Map<Date, BigDecimal> getFaturasPreview() {
		return faturasPreview;
	}

	public void setFaturasPreview(Map<Date, BigDecimal> faturas) {
		this.faturasPreview = faturas;
	}

	public List<Account> getContasCorrentes() {
		List<Account> accounts = new ArrayList<Account>();
		accounts.addAll(getContasBancos());
		accounts.addAll(getContasDinheiro());
		return accounts;
	}

	public List<Account> getContasBancos() {
		return bankAccountService.findContasBancos();
	}

	public List<Account> getContasCartoes() {
		return bankAccountService.findContasCartoes();
	}

	public List<Account> getContasDinheiro() {
		return bankAccountService.findContasDinheiro();
	}

	public boolean getIsContaBanco() {
		return getObjeto() instanceof BankAccount;
	}

	public boolean getIsContaDinheiro() {
		return getObjeto() instanceof MoneyAccount;
	}

	public boolean getIsContaCartao() {
		return getObjeto() instanceof CreditCardAccount;
	}

}
