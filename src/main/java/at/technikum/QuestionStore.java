package at.technikum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionStore {



    public void update(Connection c, int id, String text, String type, String difficulty) throws SQLException {
        String sql = "UPDATE questions SET text=?, type=?, difficulty=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, text);
            ps.setString(2, type);
            ps.setString(3, difficulty);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, int id) throws SQLException {
        String sql = "DELETE FROM questions WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<Question> findAll(Connection c) throws SQLException {
        String sql = "SELECT id, difficulty, text FROM questions ORDER BY id";
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
    public int insert(Connection c, String text, String type, String difficulty) throws SQLException {
        String sql = "INSERT INTO questions (text, type, difficulty) VALUES (?,?,?)";
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
}
