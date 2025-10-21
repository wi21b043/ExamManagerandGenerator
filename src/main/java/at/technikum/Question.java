package at.technikum;

public class Question {
    public final int id;
    public final String difficulty;
    public final String text;

    public Question(int id, String difficulty, String text) {
        this.id = id;
        this.difficulty = difficulty;
        this.text = text;
    }
}
