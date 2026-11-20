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

    public int insertTransaction(Transaction transaction) throws SQLException{
        String sql = "INSERT INTO TRANSACTIONS (date, description, type, amount, user_id) " +
                "VALUES (?,?,?,?,?) RETURNING transaction_id";
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setString(1, transaction.getDate().toString());
            stmt.setString(2, transaction.getDescription());
            stmt.setString(3, transaction.getType());
            stmt.setInt(4, transaction.getAmount());
            stmt.setInt(5, transaction.getUserID());
            try (ResultSet rows = stmt.executeQuery()){
                if(rows.next()){
                    return rows.getInt("transaction_id");
                }
                throw new SQLException("database didn't return a new transaction ID");
            }
        }
    }

    public boolean updateTransaction(Transaction transaction) throws SQLException {
        String sql = "UPDATE TRANSACTIONS " +
                "SET date = ?, description = ?, type = ?, amount = ?, user_id = ? " +
                "WHERE transaction_id = ?";

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setString(1, transaction.getDate().toString());
            stmt.setString(2, transaction.getDescription());
            stmt.setString(3, transaction.getType());
            stmt.setInt(4, transaction.getAmount());
            stmt.setInt(5, transaction.getUserID());
            stmt.setInt(6, transaction.getTransactionID());
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean deleteTransaction(int transactionID) throws SQLException {
        String sql = "DELETE FROM TRANSACTIONS WHERE transaction_id = ?";
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setInt(1, transactionID);
            return stmt.executeUpdate() == 1;
        }
    }

    public void restoreTransaction(Transaction transaction) throws SQLException {
        String sql = "INSERT INTO TRANSACTIONS " +
                "(transaction_id, date, description, type, amount, user_id) " +
                "VALUES (?,?,?,?,?,?)";
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setInt(1, transaction.getTransactionID());
            stmt.setString(2, transaction.getDate().toString());
            stmt.setString(3, transaction.getDescription());
            stmt.setString(4, transaction.getType());
            stmt.setInt(5, transaction.getAmount());
            stmt.setInt(6, transaction.getUserID());

            stmt.executeUpdate();
        }
    }
}
