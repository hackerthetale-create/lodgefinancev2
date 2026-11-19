package lodgefinance.service;

import lodgefinance.database.TransactionRepository;
import lodgefinance.model.Transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class TransactionManager {
    private List<Transaction> transactions;
    private final Stack<Transaction> deletedStack;
    private final TransactionRepository repository;

    public TransactionManager(TransactionRepository repository){
        this.repository = repository;
        this.transactions = new ArrayList<>();
        this.deletedStack = new Stack<>();
    }

    public void getTransaction() throws SQLException {
        transactions = repository.getAllTransactions();
    }

    public List<Transaction> linearSearchDescription(String keyword){
        // case insensitive search
        List<Transaction> matches = new ArrayList<>();
        // remove extra spaces and makes the text lowercase
        String searchText = keyword.trim().toLowerCase(Locale.ROOT);

        for(Transaction transaction : transactions){
            // same goes for the description
            String description = transaction.getDescription().toLowerCase(Locale.ROOT);
            if (description.contains(searchText)) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    public Transaction binarySearchID(int transactionID) {
        int low = 0;
        int high = transactions.size() - 1;
        while (low <= high){
            int mid = low + (high - low) / 2;
            Transaction current = transactions.get(mid);
            int currentID = current.getTransactionID();

            if (currentID == transactionID){
                return current;
            }
            if (currentID < transactionID){
                low = mid + 1;
            } else{
                high = mid - 1;
            }
        }
        return null;
    }

    public List<Transaction> sortTransactions(List<Transaction> records, String field, boolean asc){
        // insertion sort
        List<Transaction> sorted = new ArrayList<>(records);
        for (int i = 1; i < sorted.size(); i++){
            Transaction current = sorted.get(i);
            int pos = i;
            while (pos > 0){
                Transaction prev = sorted.get(pos - 1);
                boolean mustShift;

                // the part of the transaction where user wantts to sort by
                if (field.equals("amount")) {
                    if (asc) {
                        mustShift = prev.getAmount() > current.getAmount();
                    } else {
                        mustShift = prev.getAmount() < current.getAmount();
                    }
                } else if (field.equals("date")){
                    if (asc) {
                        // oldest date first
                        mustShift = prev.getDate().isAfter(current.getDate());
                    } else {
                        // newest date first
                        mustShift = prev.getDate().isBefore(current.getDate());
                    }
                } else if (field.equals("description")){
                    String prevText = prev.getDescription();
                    String currentText = current.getDescription();

                    // case insensitive
                    // returns positive when prevText comes later in alphabet, vice versa
                    int comparison = prevText.compareToIgnoreCase(currentText);
                    if (asc) {
                        mustShift = comparison > 0;
                    } else {
                        mustShift = comparison < 0;
                    }

                    if (!mustShift){
                        break;
                    }
                }

                // move previous transaction 1 place to the right
                sorted.set(pos,prev);
                pos--;
            }
            // insert it into the gap
            sorted.set(pos,current);
        }
        return sorted;
    }
}
