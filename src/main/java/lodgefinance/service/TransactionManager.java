package lodgefinance.service;

import lodgefinance.database.TransactionRepository;
import lodgefinance.model.Transaction;
import lodgefinance.model.User;

import java.sql.SQLException;
import java.util.*;
import java.time.LocalDate;

public class TransactionManager {
    private List<Transaction> transactions;
    private final Map<User, Stack<Transaction>> undoStacks;
    //private final Stack<Transaction> deletedStack;
    private final TransactionRepository repository;

    public TransactionManager(TransactionRepository repository){
        this.repository = repository;
        this.transactions = new ArrayList<>();
        this.undoStacks = new HashMap<>();
    }

    public void getTransaction() throws SQLException {
        transactions = repository.getAllTransactions();
    }

    public long calculateBalance(){
        long balance = 0;
        for (Transaction transaction : transactions){
            if ("Income".equals(transaction.getType())) {
                balance += transaction.getAmount();
            } else if ("Expense".equals(transaction.getType())) {
                balance -= transaction.getAmount();
            }
        }
        return balance;
    }

    public long calculateOpeningBalance(LocalDate startDate) {
        long openingBalance = 0;
        for (Transaction transaction: transactions) {
            if (transaction.getDate().isBefore(startDate)) {
                if ("Income".equals(transaction.getType())){
                    openingBalance += transaction.getAmount();
                } else {
                    openingBalance -= transaction.getAmount();
                }
            }
        }
        return openingBalance;
    }

    public long calculatePeriodIncome(LocalDate startDate, LocalDate endDate) {
        long income = 0;
        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getDate();
            boolean inPeriod = !date.isBefore(startDate) && !date.isAfter(endDate);
            if (inPeriod && "Income".equals(transaction.getType())) {
                income += transaction.getAmount();
            }
        }
        return income;
    }

    public long calculatePeriodExpenses(LocalDate startDate, LocalDate endDate) {
        long expenses = 0;
        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getDate();
            boolean inPeriod = !date.isBefore(startDate) && !date.isAfter(endDate);
            if (inPeriod && "Expense".equals(transaction.getType())) {
                expenses += transaction.getAmount();
            }
        }
        return expenses;
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
                } else if (field.equals("id")) {
                    if (asc) {
                        mustShift = prev.getTransactionID() > current.getTransactionID();
                    } else {
                        mustShift = prev.getTransactionID() < current.getTransactionID();
                    }
                } else if (field.equals("date")) {
                    if (asc) {
                        // oldest date first
                        mustShift = prev.getDate().isAfter(current.getDate());
                    } else {
                        // newest date first
                        mustShift = prev.getDate().isBefore(current.getDate());
                    }
                } else if (field.equals("description")) {
                    String prevText = prev.getDescription();
                    String currentText = current.getDescription();
                    int comparison = prevText.compareToIgnoreCase(currentText);

                    if (asc) {
                        mustShift = comparison > 0;
                    } else {
                        mustShift = comparison < 0;
                    }
                } else {
                    return sorted;
                }

                if (!mustShift) {
                    break;
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

    public void addTransaction(LocalDate date, String description, String type, int amount, int userID)
            throws SQLException {
        //set 0 as temporary id until database assigns its auto increment number
        Transaction transaction = new Transaction(0, date, type, amount, description, userID);
        if (!transaction.isValid()){
            throw new IllegalArgumentException("Invalid details");
        }
        if ("Expense".equals(type)) {
            getTransaction();
            long availableBalance = calculateBalance();
            if (amount > availableBalance) {
                throw new IllegalStateException(
                        "Expense exceeds the available lodge balance of " + availableBalance + ".");
            }
        }
        repository.insertTransaction(transaction);
        getTransaction(); // reloads the list with the new id assigned by database
    }

    public boolean updateTransaction(int transactionID, LocalDate date, String description, String type,
                                     int amount, int userID) throws SQLException{
        Transaction transaction = new Transaction(transactionID, date, type, amount, description, userID);
        if (!transaction.isValid()){
            throw new IllegalArgumentException("Invalid details");
        }
        getTransaction();
        Transaction original = binarySearchID(transactionID);
        if (original == null) {
            return false;
        }
        long projectedBalance = calculateBalance();
        // Reverse the original entry
        if ("Income".equals(original.getType())) {
            projectedBalance -= original.getAmount();
        } else {
            projectedBalance += original.getAmount();
        }
        // Apply the edited entry
        if ("Income".equals(type)) {
            projectedBalance += amount;
        } else {
            projectedBalance -= amount;
        }
        if (projectedBalance < 0) {
            throw new IllegalStateException("Edit rejected. The lodge balance would be " + projectedBalance);
        }
        boolean updated = repository.updateTransaction(transaction);
        getTransaction();
        return updated;
    }

    public boolean deleteTransaction(int transactionID, User user) throws SQLException{
        getTransaction();
        Transaction transaction = binarySearchID(transactionID);
        if (transaction == null) {
            return false;
        }
        boolean deleted = repository.deleteTransaction(transactionID);
        if(deleted) {
            Stack<Transaction> deletedStack = undoStacks.get(user);
            if (deletedStack == null) {
                deletedStack = new Stack<Transaction>();
                undoStacks.put(user, deletedStack);
            }
            deletedStack.push(transaction);
            transactions.remove(transaction);
        }
        return deleted;
    }

    public boolean undoDelete(User user) throws SQLException {
        Stack<Transaction> deletedStack = undoStacks.get(user);

        if ( deletedStack== null || deletedStack.isEmpty()) {
            return false;
        }
        Transaction transaction = deletedStack.peek();
        repository.restoreTransaction(transaction);
        deletedStack.pop();
        getTransaction();
        return true;
    }

}
