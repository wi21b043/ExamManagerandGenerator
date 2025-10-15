package at.technikum;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteSchemaCreator {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:exam_manager.db"; // 数据库文件名

        String sql =
                "CREATE TABLE IF NOT EXISTS Questions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "text TEXT NOT NULL, "
                        + "type TEXT, "
                        + "difficulty TEXT, "
                        + "topic TEXT, "
                        + "metadata TEXT, "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, "
                        + "version INTEGER DEFAULT 1); "

                        + "CREATE TABLE IF NOT EXISTS Categories ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT NOT NULL); "

                        + "CREATE TABLE IF NOT EXISTS Question_Categories ("
                        + "question_id INTEGER NOT NULL, "
                        + "category_id INTEGER NOT NULL, "
                        + "FOREIGN KEY (question_id) REFERENCES Questions(id), "
                        + "FOREIGN KEY (category_id) REFERENCES Categories(id), "
                        + "PRIMARY KEY (question_id, category_id)); "

                        + "CREATE TABLE IF NOT EXISTS Exams ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "title TEXT NOT NULL, "
                        + "created_at TEXT DEFAULT CURRENT_TIMESTAMP); "

                        + "CREATE TABLE IF NOT EXISTS Exam_Questions ("
                        + "exam_id INTEGER NOT NULL, "
                        + "question_id INTEGER NOT NULL, "
                        + "order_index INTEGER, "
                        + "FOREIGN KEY (exam_id) REFERENCES Exams(id), "
                        + "FOREIGN KEY (question_id) REFERENCES Questions(id), "
                        + "PRIMARY KEY (exam_id, question_id));";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // 打开外键支持（SQLite 默认关闭）
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 执行建表语句
            stmt.executeUpdate(sql);
            System.out.println("✅ All tables created successfully.");

        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
}
