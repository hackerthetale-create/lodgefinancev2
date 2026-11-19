package lodgefinance.model;

import java.time.LocalDate;

public class Transaction {
    private final int transactionID;
    private final LocalDate date;
    private final String type;
    private final int amount;
    private final String description;
    private final int userID;

    public Transaction(int transactionID, LocalDate date, String type, int amount,
                       String description, int userID){
        this.transactionID = transactionID;
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.userID = userID;
    }

    public int getTransactionID() {
        return transactionID;
    }

    public LocalDate getDate(){
        return date;
    }

    public String getType(){
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    private int getUserID() {
        return userID;
    }

    public boolean isValid() {
        if (date == null) {
            return false;
        }
        if (description == null || description.isBlank()){
            return false;
        }
        if (!"Income".equals(type) && !"Expense".equals(type)){
            return false;
        }
        return amount > 0;
    }
}
