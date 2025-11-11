package at.technikum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
                return keys.next() ? keys.getInt(1) : -1;
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
        }
    }

    // 删除题目所有版本（同时清理外键依赖）
    public void delete(Connection c, int id) throws SQLException {
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
    }

    // 统一删除：先删依赖表，再删 Questions（带事务 & 外键开启）
    public boolean deleteQuestion(Connection c, int questionId) throws SQLException {
        // 开启外键（已开启也不影响）
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }

        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
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
            // 3) 删除所有历史版本（你原来没删这一张）
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM QuestionVersions WHERE question_id = ?")) {
                ps.setInt(1, questionId);
                ps.executeUpdate();
            }
            // 4) 最后删题目本体
            int affected;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM Questions WHERE id = ?")) {
                ps.setInt(1, questionId);
                affected = ps.executeUpdate();
            }

            c.commit();
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

                    // 你们的 QuestionVersion 构造函数是 (id, text, difficulty, version, createdAt, updatedAt)
                    // 版本表暂时没有 updated_at，这里先用 created_at 占位
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




    //回滚：把题目恢复到指定版本（会触发生成一个新的版本号）
    public boolean rollbackToVersion(Connection c, int questionId, int targetVersion) throws SQLException {
        //读出目标版本的内容
        String selectSql = """
        SELECT text, type, difficulty, topic, metadata
        FROM QuestionVersions
        WHERE question_id = ? AND version = ?
    """;

        try (PreparedStatement ps1 = c.prepareStatement(selectSql)) {
            ps1.setInt(1, questionId);
            ps1.setInt(2, targetVersion);
            try (ResultSet rs = ps1.executeQuery()) {
                if (!rs.next()) return false; // 该版本不存在

                String text       = rs.getString("text");
                String type       = rs.getString("type");
                String difficulty = rs.getString("difficulty");
                String topic      = rs.getString("topic");
                String metadata   = rs.getString("metadata");

                //覆盖 Questions 当前内容（触发器会自动写入新版本）
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





}
