package at.technikum;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionStore {

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
