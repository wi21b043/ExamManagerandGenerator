package at.technikum;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class QuestionStore {

    // 读取所有问题（只取最新版本）
    public List<Question> findAll(Connection c) throws SQLException {
        String sql = """
    SELECT
      question_id AS id,
      difficulty,
      text
    FROM QuestionLatest
    ORDER BY question_id
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

    // 插入新问题（初始版本 = V1）
    public int insert(Connection c, String text, String type, String difficulty) throws SQLException {
        String sql = "INSERT INTO Questions (text, type, difficulty, version, created_at, updated_at) VALUES (?,?,?,1,datetime('now'),datetime('now'))";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : -1;
                System.out.println("[DB] Inserted question ID=" + id); // ✅ 优化4：日志
                return id;
            }
        }
    }

    // 编辑题目
    public void update(Connection c, int id, String text, String type, String difficulty) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE Questions SET text = ?, type = ?, difficulty = ?, updated_at = datetime('now') WHERE id = ?")) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.setInt(4, id);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No question with id = " + id);
            }
            System.out.println("[DB] Updated question ID=" + id); // ✅ 优化4：日志
        }
    }

    // 删除题目所有版本（同时清理外键依赖）
    public void delete(Connection c, int id) throws SQLException {
        //  优化1：删除前清除 Answers 表
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Answers WHERE question_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            System.out.println("[DB] Deleted answers for question ID=" + id);
        }

        //删除 Exam_Questions 中引用的记录
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Exam_Questions WHERE question_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        //删除 Question_Categories 中引用的记录
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Question_Categories WHERE question_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        //删除 Questions 表中的所有版本
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM Questions WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        System.out.println("[DB] Deleted question ID=" + id); // ✅ 优化4：日志
    }

    // 统一删除：先删依赖表，再删 Questions（带事务 & 外键开启）
    public boolean deleteQuestion(Connection c, int questionId) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }

        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            //  优化1：删除 Answers（事务安全）
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Answers WHERE question_id = ?")) {
                ps.setInt(1, questionId);
                ps.executeUpdate();
                System.out.println("[DB] Deleted answers for question ID=" + questionId);
            }

            // 1) 删除已加入试卷的记录
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Exam_Questions WHERE question_id = ?")) {
                ps.setInt(1, questionId);
                ps.executeUpdate();
            }

            // 2) 删除题目与分类的关系
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Question_Categories WHERE question_id = ?")) {
                ps.setInt(1, questionId);
                ps.executeUpdate();
            }

            // 3) 删除所有历史版本
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM QuestionVersions WHERE question_id = ?")) {
                ps.setInt(1, questionId);
                ps.executeUpdate();
            }

            // 4) 删除题目本体
            int affected;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Questions WHERE id = ?")) {
                ps.setInt(1, questionId);
                affected = ps.executeUpdate();
            }

            c.commit();
            System.out.println("[DB] Deleted question and related data ID=" + questionId); // ✅ 优化4：日志
            return affected == 1;
        } catch (SQLException ex) {
            try { c.rollback(); } catch (SQLException ignore) {}
            throw ex;
        } finally {
            try { c.setAutoCommit(oldAuto); } catch (SQLException ignore) {}
        }
    }

    //删除题目与类别关系（用于更新或删除）
    public void deleteQuestionCategories(Connection c, int qId) throws SQLException {
        String sql = "DELETE FROM Question_Categories WHERE question_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qId);
            ps.executeUpdate();
        }
    }

    //插入题目与类别的关联（避免重复）
    public void linkQuestionCategory(Connection c, int qId, int cId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO Question_Categories (question_id, category_id) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qId);
            ps.setInt(2, cId);
            ps.executeUpdate();
        }
    }

    //查询指定题目的所有版本
    public List<QuestionVersion> findVersions(Connection c, int questionId) throws SQLException {
        String sql = """
        SELECT
          question_id AS id,
          text,
          difficulty,
          version,
          created_at
        FROM QuestionVersions
        WHERE question_id = ?
        ORDER BY version DESC
    """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<QuestionVersion> list = new ArrayList<>();
                while (rs.next()) {
                    int id          = rs.getInt("id");
                    String text     = rs.getString("text");
                    String diff     = rs.getString("difficulty");
                    int ver         = rs.getInt("version");
                    String created  = rs.getString("created_at");

                    list.add(new QuestionVersion(id, text, diff, ver, created, created));
                }
                return list;
            }
        }
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
        public int getVersion() { return version; }
        public String getDifficulty() { return difficulty; }
        public String getText() { return text; }
        public String getCreated() { return createdAt; }
        public String getUpdated() { return updatedAt; }
    }

    //回滚：把题目恢复到指定版本
    public boolean rollbackToVersion(Connection c, int questionId, int targetVersion) throws SQLException {
        String selectSql = """
        SELECT text, type, difficulty, topic, metadata
        FROM QuestionVersions
        WHERE question_id = ? AND version = ?
    """;

        try (PreparedStatement ps1 = c.prepareStatement(selectSql)) {
            ps1.setInt(1, questionId);
            ps1.setInt(2, targetVersion);
            try (ResultSet rs = ps1.executeQuery()) {
                if (!rs.next()) return false;

                String text       = rs.getString("text");
                String type       = rs.getString("type");
                String difficulty = rs.getString("difficulty");
                String topic      = rs.getString("topic");
                String metadata   = rs.getString("metadata");

                String updateSql = """
                UPDATE Questions
                SET text = ?, type = ?, difficulty = ?, topic = ?, metadata = ?
                WHERE id = ?
            """;
                try (PreparedStatement ps2 = c.prepareStatement(updateSql)) {
                    ps2.setString(1, text);
                    ps2.setString(2, type);
                    ps2.setString(3, difficulty);
                    ps2.setString(4, topic);
                    ps2.setString(5, metadata);
                    ps2.setInt(6, questionId);
                    return ps2.executeUpdate() == 1;
                }
            }
        }
    }

    // ====== 答案相关操作 ======
    // 优化2：通过 Java 保证唯一性（如果已存在则更新）
    public void insertAnswer(Connection c, int questionId, String answer) throws SQLException {
        String check = "SELECT COUNT(*) FROM Answers WHERE question_id = ?";
        try (PreparedStatement psCheck = c.prepareStatement(check)) {
            psCheck.setInt(1, questionId);
            ResultSet rs = psCheck.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                updateAnswer(c, questionId, answer);
                return;
            }
        }

        String sql = "INSERT INTO Answers (question_id, answer_text) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            ps.setString(2, answer);
            ps.executeUpdate();
            System.out.println("[DB] Inserted answer for question ID=" + questionId); // ✅ 优化4
        }
    }

    public void updateAnswer(Connection c, int questionId, String answer) throws SQLException {
        String sql = "UPDATE Answers SET answer_text = ? WHERE question_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, answer);
            ps.setInt(2, questionId);
            ps.executeUpdate();
            System.out.println("[DB] Updated answer for question ID=" + questionId); // ✅ 优化4
        }
    }

    public String findAnswer(Connection c, int questionId) throws SQLException {
        String sql = "SELECT answer_text FROM Answers WHERE question_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("answer_text") : "";
        }
    }


    // 优化3：批量查询所有答案
    public Map<Integer, String> findAnswersForQuestions(Connection c, List<Integer> questionIds) throws SQLException {
        if (questionIds == null || questionIds.isEmpty()) return Collections.emptyMap();

        String placeholders = questionIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT question_id, answer_text FROM Answers WHERE question_id IN (" + placeholders + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < questionIds.size(); i++) {
                ps.setInt(i + 1, questionIds.get(i));
            }
            Map<Integer, String> map = new HashMap<>();
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getInt("question_id"), rs.getString("answer_text"));
            }
            return map;
        }
    }
    public List<Question> search(Connection c, String keyword) throws SQLException {
        String sql = """
        SELECT q.id, q.difficulty, q.text
        FROM Questions q
        WHERE q.text LIKE ?
        ORDER BY q.id
    """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            List<Question> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new Question(
                        rs.getInt("id"),
                        rs.getString("difficulty"),
                        rs.getString("text")
                ));
            }
            return list;
        }
    }

}
