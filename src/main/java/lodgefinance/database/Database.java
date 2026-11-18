package lodgefinance.database;

import lodgefinance.model.User;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class Database{
    private final Path databasePath;

    public Database(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:"+databasePath);
        try (Statement stat = conn.createStatement()) {
            stat.execute("PRAGMA foreign_keys = ON");
            return conn;
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }

    //create and initalize the database file
    public void initialize() throws IOException, SQLException{
        //create the data directory, usually not needed cause it is already created manually
        Files.createDirectories(databasePath.getParent());
        String schema;

        try(InputStream input = Database.class.getResourceAsStream("/database/schema.sql")) {
            if(input == null) {
                throw new IOException("Missing database/schema.sql resource, cann't create db!!!");
            }
            schema  = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try(Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try(Statement stat = conn.createStatement()) {
                    for(String sql : schema.split(";")) {
                        if(!sql.isBlank()) {
                            stat.executeUpdate(sql);
                        }
                    }
                }
                //schema created, now add sample data
                addInitialUsers(conn);
                addSampleTransactions(conn);
                conn.commit();
            } catch(SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    //add 3 initial user records
    private void addInitialUsers(Connection conn) throws SQLException{
        String[][] users = {
                {"treasurer", "LodgeDemo!2026"},
                {"lodgechief", "ChiefDemo!2026"},
                {"chinaclan", "ChinaDemo!2026"}
        };

        String sql = //"INSERT INTO USERS(username, password) VALUES (?, ?) ";

                "INSERT INTO USERS(username, password) " +
                "SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM USERS WHERE username = ?)";

        try (PreparedStatement stat = conn.prepareStatement(sql)) {
            for(String[] user : users ) {
                stat.setString(1, user[0]);
                stat.setString(2, user[1]);
                stat.setString(3, user[0]);
            stat.executeUpdate();
            }
        }
    }

    //add sample transaction entries in db.
    private void addSampleTransactions(Connection conn) throws SQLException {
        Map<String, Integer> userIDs = new LinkedHashMap<>();
        try (Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery("SELECT user_id, username FROM USERS")) {
            while (rows.next()) {
                userIDs.put(rows.getString("username"), rows.getInt("user_id"));
            }
        }
        String[][] samples = {
                {"2026-11-14", "Membership dues", "Income", "25", "treasurer"},
                {"2026-11-13", "Event supplies", "Expense", "48", "treasurer"},
                {"2026-11-12", "Fall fellowship registration", "Income", "150", "treasurer"},
                {"2026-11-10", "Service day registration", "Income", "60", "lodgechief"},
                {"2026-11-11", "Service day supplies", "Expense", "75", "lodgechief"},
                {"2026-11-09", "Membership dues", "Income", "25", "lodgechief"},
                {"2026-11-08", "Lodge donation", "Income", "100", "chinaclan"},
                {"2026-11-07", "Newsletter printing", "Expense", "35", "chinaclan"},
                {"2026-11-06", "Fellowship refreshments contribution", "Income", "40", "chinaclan"}
        };
        String sql = "INSERT INTO TRANSACTIONS (date, description, type, amount, user_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (String[] sample : samples) {
                statement.setString(1, sample[0]);
                statement.setString(2, sample[1]);
                statement.setString(3, sample[2]);
                statement.setInt(4, Integer.parseInt(sample[3]));
                statement.setInt(5, userIDs.get(sample[4]));
                statement.executeUpdate();
            }
        }
    }

    public User checkLogin(String username, String password) throws SQLException {
        String sql = "SELECT user_id, username, password FROM USERS WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql))
        {
            stmt.setString(1, username);

            try (ResultSet results = stmt.executeQuery())
            {
                if (results.next())
                {
                    String storedPassword = results.getString("password");
                    if (storedPassword.equals(password))
                    {
                        int userId = results.getInt("user_id");
                        return new User(userId, username, storedPassword);
                    }
                }
            }
        }
        return null;
    }
}