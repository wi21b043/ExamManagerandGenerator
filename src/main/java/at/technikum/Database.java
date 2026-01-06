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

        // ✅ 程序启动时自动创建所需表
        try (Connection c = DriverManager.getConnection(URL);
             Statement st = c.createStatement()) {

            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA busy_timeout = 5000;"); // 避免 database locked

            // ✅ 确保 Questions 表存在（防止首次运行外键报错）
            st.execute("""
                CREATE TABLE IF NOT EXISTS Questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    type TEXT,
                    difficulty TEXT,
                    topic TEXT,
                    metadata TEXT,
                    version INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now'))
                );
            """);

            //  自动创建 Answers 表（带外键）
            st.execute("""
                CREATE TABLE IF NOT EXISTS Answers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    question_id INTEGER NOT NULL,
                    answer_text TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    FOREIGN KEY (question_id) REFERENCES Questions(id) ON DELETE CASCADE
                );
            """);

            System.out.println("[DB] Database initialized successfully.");
            System.out.println("[DB] Answers table checked/created.");
        } catch (SQLException ex) {
            System.err.println("[DB INIT ERROR] " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Database() {}

    public static Connection get() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA busy_timeout = 5000;"); //  防止“database locked”
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
