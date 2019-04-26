package br.com.victorpfranca.mybudget.transaction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.enterprise.context.RequestScoped;

import br.com.victorpfranca.mybudget.InOut;
import br.com.victorpfranca.mybudget.account.Account;
import br.com.victorpfranca.mybudget.category.Category;
import br.com.victorpfranca.mybudget.view.MonthYear;

@RequestScoped
public class TransactionQuery implements Serializable {

	private static final long serialVersionUID = 1L;

	public List<Transaction> transactions(TransactionsFilter transactionsFilter) {

		List<Transaction> transactions = new ArrayList<Transaction>();
		for (int i = 0; i < 25; i++) {
			CheckingAccountTransaction transaction = new CheckingAccountTransaction();
			transaction.setStatus(TransactionStatus.CONFIRMADO);
			transaction.setData(Calendar.getInstance().getTime());
			transaction.setContaDestino(new Account("Banco do Brasil"));
			transaction.setCategoria(new Category("Categoria Teste", InOut.E));
			transaction.setValor(BigDecimal.TEN);
			transaction.setInOut(InOut.E);
			transaction.setSaldo(BigDecimal.TEN);
			transactions.add(transaction);
		}

		return transactions;
	}

	public List<Transaction> extratoCartao(TransactionsFilter transactionsFilter) {
		return new ArrayList<Transaction>();
	}

	public Transaction recuperarLancamento(Integer id, MonthYear monthYear) {
		return new Transaction();
	}

}
