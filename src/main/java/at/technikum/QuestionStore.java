package at.technikum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionStore {

    // 读取所有问题（只取最新版本）
    public List<Question> findAll(Connection c) throws SQLException {
        String sql = """
            SELECT q1.id, q1.difficulty, q1.text
            FROM Questions q1
            WHERE q1.version = (
                SELECT MAX(q2.version)
                FROM Questions q2
                WHERE q2.id = q1.id
            )
            ORDER BY q1.id
        """;
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Question> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Question(
                        rs.getInt("id"),
                        rs.getString("difficulty"),
                        rs.getString("text")
                ));
            }
            return out;
        }
    }

    // 插入新问题（初始版本 = 1）
    public int insert(Connection c, String text, String type, String difficulty) throws SQLException {
        String sql = "INSERT INTO Questions (text, type, difficulty, version, created_at, updated_at) VALUES (?,?,?,1,datetime('now'),datetime('now'))";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    /**
     * 更新题目时自动保留旧版本：
     * 不覆盖旧记录，而是新增一条新记录（新的 id，旧版本保留）
     */
    public void update(Connection c, int oldId, String text, String type, String difficulty) throws SQLException {
        // Step 1: 获取旧版本号
        String sel = "SELECT version, created_at FROM Questions WHERE id=? ORDER BY version DESC LIMIT 1";
        int newVersion = 1;
        String createdAt = null;
        try (PreparedStatement ps = c.prepareStatement(sel)) {
            ps.setInt(1, oldId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                newVersion = rs.getInt("version") + 1;
                createdAt = rs.getString("created_at");
            }
        }

        // Step 2: 插入新记录（不复用旧 id，让 SQLite 自动生成）
        String ins = "INSERT INTO Questions (text, type, difficulty, version, created_at, updated_at) VALUES (?,?,?,?,?,datetime('now'))";
        try (PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.setInt(4, newVersion);
            ps.setString(5, createdAt == null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) : createdAt);
            ps.executeUpdate();
        }

        System.out.println("✅ New version inserted successfully (old ID " + oldId + " → version " + newVersion + ")");
    }

    // 删除题目所有版本（同时清理外键依赖）
    public void delete(Connection c, int id) throws SQLException {
        // Step 1: 删除 Exam_Questions 中引用的记录
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Exam_Questions WHERE question_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        // Step 2: 删除 Question_Categories 中引用的记录
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Question_Categories WHERE question_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        // Step 3: 删除 Questions 表中的所有版本
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Questions WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // 删除题目与类别关系（用于更新或删除）
    public void deleteQuestionCategories(Connection c, int qId) throws SQLException {
        String sql = "DELETE FROM Question_Categories WHERE question_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qId);
            ps.executeUpdate();
        }
    }

    // 插入题目与类别的关联（避免重复）
    public void linkQuestionCategory(Connection c, int qId, int cId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO Question_Categories (question_id, category_id) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qId);
            ps.setInt(2, cId);
            ps.executeUpdate();
        }
    }

    // === 新增：查询指定题目的所有版本 ===
    public List<QuestionVersion> findVersions(Connection c, int baseId) throws SQLException {
        String sql = """
            SELECT id, text, difficulty, version, created_at, updated_at
            FROM Questions
            WHERE id = ? OR (
                text = (SELECT text FROM Questions WHERE id = ?)
            )
            ORDER BY version DESC
        """;
        List<QuestionVersion> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, baseId);
            ps.setInt(2, baseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new QuestionVersion(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getString("difficulty"),
                        rs.getInt("version"),
                        rs.getString("created_at"),
                        rs.getString("updated_at")
                ));
            }
        }
        return list;
    }

    // 内部数据模型类
    public static class QuestionVersion {
        public final int id;
        public final String text;
        public final String difficulty;
        public final int version;
        public final String createdAt;
        public final String updatedAt;
        public QuestionVersion(int id, String text, String difficulty, int version, String createdAt, String updatedAt) {
            this.id = id;
            this.text = text;
            this.difficulty = difficulty;
            this.version = version;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
