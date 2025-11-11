package at.technikum;

import java.nio.file.Path;
import java.sql.*;

public final class Database {

    private static final String URL = "jdbc:sqlite:" + Path.of("exam_manager.db").toAbsolutePath();

    static {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found on classpath", e);
        }
    }

    private Database() {}


    public static Connection get() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }


    public static void printTables() {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
        try (Connection c = get(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("Tables in DB (" + URL + "):");
            while (rs.next()) System.out.println(" - " + rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
