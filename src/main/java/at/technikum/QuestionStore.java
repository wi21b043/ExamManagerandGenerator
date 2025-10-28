package at.technikum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionStore {

    // 读取所有问题（不含类别）
    public List<Question> findAll(Connection c) throws SQLException {
        String sql = "SELECT id, difficulty, text FROM Questions ORDER BY id";
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

    // 插入新问题
    public int insert(Connection c, String text, String type, String difficulty) throws SQLException {
        String sql = "INSERT INTO Questions (text, type, difficulty) VALUES (?,?,?)";
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

    // 更新问题
    public void update(Connection c, int id, String text, String type, String difficulty) throws SQLException {
        String sql = "UPDATE Questions SET text=?, type=?, difficulty=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    // 删除问题
    public void delete(Connection c, int id) throws SQLException {
        String sql = "DELETE FROM Questions WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // 删除题目与类别的关系（用于更新或删除）
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
}
