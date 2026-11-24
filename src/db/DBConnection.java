package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Allows environment variable overrides for Cloud deployments (e.g., Render, Railway, Heroku)
    // Falls back to local MySQL or Aiven Cloud default
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/payrollpro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "Naresh@8346";

    private DBConnection() {
        // Prevent instantiation
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_URL;
        }
        if (user == null || user.trim().isEmpty()) {
            user = DEFAULT_USER;
        }
        if (password == null) {
            password = DEFAULT_PASSWORD;
        }

        return DriverManager.getConnection(url, user, password);
    }
}
