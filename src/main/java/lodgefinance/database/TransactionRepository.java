package lodgefinance.database;

import lodgefinance.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class TransactionRepository {
    private final Database database;

    public TransactionRepository(Database database){
        this.database = database;
    }

    public List<Transaction> getAllTransactions() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT transaction_id, date, type, amount, description, user_id " +
                "FROM TRANSACTIONS ORDER BY transaction_id ASC";
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rows = stmt.executeQuery()){

            while (rows.next()) {
                Transaction transaction = new Transaction(
                        rows.getInt("transaction_id"),
                        LocalDate.parse(rows.getString("date")),
                        rows.getString("type"),
                        rows.getInt("amount"),
                        rows.getString("description"),
                        rows.getInt("user_id")
                );

                transactions.add(transaction);
            }
        }
        return transactions;
    }
}
