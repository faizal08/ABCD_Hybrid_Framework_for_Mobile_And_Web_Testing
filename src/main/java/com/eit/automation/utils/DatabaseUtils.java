package com.eit.automation.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Utility class for Database Operations.
 * Designed to be zero-maintenance: it pulls credentials from the
 * configuration properties loaded at runtime.
 */
public class DatabaseUtils {

    /**
     * Executes a SQL command (DELETE/UPDATE) using the configuration loaded in Main.
     * * @param sql    The SQL query to execute.
     * @param config The Properties object already loaded in your Main class.
     * @throws Exception if database config is missing or SQL is unsafe.
     */
    public static void executeCleanup(String sql, Properties config) throws Exception {

        // 1. DYNAMIC CREDENTIAL FETCHING
        // These keys must exist in your .properties files (config.properties, we1.properties, etc.)
        String url = config.getProperty("db.url");
        String user = config.getProperty("db.user");
        String pass = config.getProperty("db.password");

        // Null check to prevent NullPointerException if properties are missing
        if (url == null || user == null || pass == null) {
            throw new Exception("❌ Database Configuration Error: 'db.url', 'db.user', or 'db.password' missing in properties file.");
        }

        // 2. SAFETY FIREWALL
        // Prevents execution of DELETE or UPDATE statements that lack a WHERE clause
        String normalizedSql = sql.trim().toLowerCase();
        if (normalizedSql.startsWith("delete") || normalizedSql.startsWith("update")) {
            if (!normalizedSql.contains("where")) {
                throw new Exception("🛑 ACCIDENTAL DELETION PREVENTED: SQL command missing 'WHERE' clause. Query: " + sql);
            }
        }

        // 3. DATABASE EXECUTION
        // Uses try-with-resources to ensure Connection and Statement are closed automatically
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            System.out.println("🗄️ Database Action: " + sql);
            int affectedRows = stmt.executeUpdate(sql);
            System.out.println("✅ Operation Successful. Rows affected: " + affectedRows);

        } catch (SQLException e) {
            throw new Exception("❌ JDBC Error: " + e.getMessage());
        }
    }
}