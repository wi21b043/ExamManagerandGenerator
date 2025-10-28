package at.technikum;

import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class UiApp extends Application {

    private final QuestionStore store = new QuestionStore();

    // ===== Data structures =====
    static class Category { int id; String name; }
    static class Question { int id; String text; String difficulty; }
    static class QuestionCategory { int question_id; int category_id; }
    static class Exam { int id; String name; String created_at; }
    static class ExamQuestion { int exam_id; int question_id; int position; }

    private final Map<Integer, Category> Categories = new LinkedHashMap<>();
    private final Map<Integer, Question> Questions = new LinkedHashMap<>();
    private final List<QuestionCategory> Question_Categories = new ArrayList<>();

    private final AtomicInteger catSeq = new AtomicInteger(1);
    private final AtomicInteger qSeq   = new AtomicInteger(1);

    // ===== Table model =====
    public static class QuestionRow {
        private final int id; private final String difficulty; private final String text; private final List<String> categories;
        public QuestionRow(int id, String difficulty, String text, List<String> categories){
            this.id=id;this.difficulty=difficulty;this.text=text;this.categories=categories;
        }
        public int getId(){return id;}
        public String getDifficulty(){return difficulty;}
        public String getText(){return text;}
        public String getCategoriesCsv(){return String.join(", ", categories);}
        public List<String> getCategories(){return categories;}
    }

    private final TableView<QuestionRow> table = new TableView<>();
    private final ObservableList<QuestionRow> data = FXCollections.observableArrayList();

    @Override public void start(Stage stage){
        stage.setTitle("Exam Manager and Generator");

        Button btnLoad  = new Button("Load");
        Button btnAdd   = new Button("Add Question");
        Button btnEdit  = new Button("Edit Selected");
        Button btnDel   = new Button("Delete Selected");

        btnLoad.setOnAction(e -> loadFromDatabase());
        btnAdd.setOnAction(e -> openAddOrEditDialog(stage, null));
        btnEdit.setOnAction(e -> {
            QuestionRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { warn("Select a row."); return; }
            openAddOrEditDialog(stage, sel);
        });
        btnDel.setOnAction(e -> deleteSelected());

        HBox top = new HBox(10, btnLoad, btnAdd, btnEdit, btnDel);
        top.setPadding(new Insets(10)); top.setAlignment(Pos.CENTER_LEFT);

        TableColumn<QuestionRow, Number> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<QuestionRow, String> cDiff = new TableColumn<>("Difficulty");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        TableColumn<QuestionRow, String> cCat = new TableColumn<>("Categories");
        cCat.setCellValueFactory(new PropertyValueFactory<>("categoriesCsv"));
        TableColumn<QuestionRow, String> cText = new TableColumn<>("Text");
        cText.setCellValueFactory(new PropertyValueFactory<>("text"));
        table.getColumns().addAll(cId, cDiff, cCat, cText);
        table.setItems(data);

        BorderPane root = new BorderPane(); root.setTop(top); root.setCenter(table);
        BorderPane.setMargin(table, new Insets(10));
        stage.setScene(new Scene(root, 1080, 560)); stage.show();
    }

    // ===== Load data from DB =====
    private void loadFromDatabase() {
        data.clear();
        loadCategoriesFromDb();

        try (var c = Database.get()) {
            var questions = store.findAll(c);

            Map<Integer, List<String>> catsByQuestion = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT qc.question_id, c.name FROM Question_Categories qc " +
                            "JOIN Categories c ON qc.category_id = c.id")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int qId = rs.getInt("question_id");
                    String catName = rs.getString("name");
                    catsByQuestion.computeIfAbsent(qId, k -> new ArrayList<>()).add(catName);
                }
            }

            for (var q : questions) {
                List<String> cats = catsByQuestion.getOrDefault(q.id, Collections.emptyList());
                data.add(new QuestionRow(q.id, q.difficulty, q.text, cats));
            }

            info("Loaded " + questions.size() + " questions from database.");
        } catch (Exception ex) {
            warn("Failed to load from DB: " + ex.getMessage());
        }
    }

    // ===== Add/Edit Question =====
    private void openAddOrEditDialog(Stage owner, QuestionRow existing){
        Dialog<Boolean> dlg = new Dialog<>(); dlg.initOwner(owner);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle(existing == null ? "Add Question" : "Edit Question");

        TextArea taText = new TextArea(existing == null ? "" : existing.getText());
        taText.setPrefRowCount(6);

        ComboBox<String> cbDiff = new ComboBox<>(FXCollections.observableArrayList("Easy","Medium","Hard"));
        cbDiff.getSelectionModel().select(existing == null ? 0 :
                switch(existing.getDifficulty()){case "Medium"->1; case "Hard"->2; default->0;});

        // ==== New intuitive multi-select with CheckBoxes ====
        ObservableList<String> allCats = FXCollections.observableArrayList(Categories.values().stream()
                .map(c -> c.name).sorted().toList());
        ListView<String> lvCats = new ListView<>(allCats);
        lvCats.setCellFactory(CheckBoxListCell.forListView(item -> {
            SimpleBooleanProperty prop = new SimpleBooleanProperty();
            prop.addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected && !selectedCats.contains(item)) selectedCats.add(item);
                else if (!isNowSelected) selectedCats.remove(item);
            });
            prop.set(selectedCats.contains(item));
            return prop;
        }));
        lvCats.setPrefHeight(150);

        // Collect selected categories for saving
        selectedCats.clear();
        if (existing != null) selectedCats.addAll(existing.getCategories());

        TextField tfNewCat = new TextField(); tfNewCat.setPromptText("Add new category");
        Button btnAddCat = new Button("+");
        btnAddCat.setOnAction(e -> {
            String n = opt(tfNewCat.getText());
            if (!n.isEmpty() && !allCats.contains(n)) {
                int id = insertCategoryIntoDb(n);
                if (id > 0) {
                    allCats.add(n);
                    FXCollections.sort(allCats);
                    info("Category added: " + n);
                }
            }
            tfNewCat.clear();
        });

        Button btnDelCat = new Button("–");
        btnDelCat.setOnAction(e -> {
            String selected = lvCats.getSelectionModel().getSelectedItem();
            if (selected == null) { warn("Select a category to delete."); return; }
            if (deleteCategoryFromDb(selected)) {
                allCats.remove(selected);
                selectedCats.remove(selected);
                info("Category deleted: " + selected);
            }
        });

        Label lblHint = new Label("Select one or more categories below:");
        lblHint.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        GridPane gp = new GridPane(); gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Difficulty:"), cbDiff);
        gp.add(new Label("Text:"),0,1); gp.add(taText,1,1);
        gp.add(lblHint,1,2);
        gp.add(lvCats,1,3);
        gp.add(new HBox(6, tfNewCat, btnAddCat, btnDelCat),1,4);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return false;
            String text = opt(taText.getText());
            if (text.isEmpty()) { warn("Text required."); return false; }
            String diff = cbDiff.getValue();

            List<Integer> catIds = new ArrayList<>();
            for (String n : selectedCats) if (!n.isBlank()) catIds.add(ensureCategory(n.trim()));

            if (existing == null) {
                try (var c = Database.get()) {
                    int qid = store.insert(c, text, "short", diff);
                    for (int cid : catIds) store.linkQuestionCategory(c, qid, cid);
                    info("Question saved (ID: " + qid + ")");
                } catch (Exception ex) { warn("Failed to save: " + ex.getMessage()); }
            } else {
                try (var c = Database.get()) {
                    store.update(c, existing.getId(), text, "short", diff);
                    store.deleteQuestionCategories(c, existing.getId());
                    for (int cid : catIds) store.linkQuestionCategory(c, existing.getId(), cid);
                    info("Question updated (ID: " + existing.getId() + ")");
                } catch (Exception ex) { warn("Failed to update: " + ex.getMessage()); }
            }
            loadFromDatabase();
            return true;
        });
        dlg.showAndWait();
    }

    // Global variable for selected categories
    private final Set<String> selectedCats = new HashSet<>();

    private void deleteSelected() {
        QuestionRow sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { warn("Select a row."); return; }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete question #" + sel.getId() + "?",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        a.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try (var c = Database.get()) {
                    store.deleteQuestionCategories(c, sel.getId());
                    store.delete(c, sel.getId());
                    loadFromDatabase();
                    info("Question deleted.");
                } catch (Exception ex) {
                    warn("Failed to delete: " + ex.getMessage());
                }
            }
        });
    }

    // ===== Category helpers =====
    private void loadCategoriesFromDb() {
        Categories.clear();
        try (var c = Database.get()) {
            PreparedStatement ps = c.prepareStatement("SELECT id, name FROM Categories ORDER BY name");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Category cat = new Category();
                cat.id = rs.getInt("id");
                cat.name = rs.getString("name");
                Categories.put(cat.id, cat);
            }
        } catch (Exception ex) { warn("Failed to load categories: " + ex.getMessage()); }
    }

    private int ensureCategory(String name) { return insertCategoryIntoDb(name); }

    private int insertCategoryIntoDb(String name) {
        try (var c = Database.get()) {
            PreparedStatement find = c.prepareStatement("SELECT id FROM Categories WHERE LOWER(name)=LOWER(?)");
            find.setString(1, name);
            ResultSet fr = find.executeQuery();
            if (fr.next()) return fr.getInt("id");

            PreparedStatement ps = c.prepareStatement("INSERT INTO Categories(name) VALUES(?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newId = rs.getInt(1);
                Category cat = new Category(); cat.id = newId; cat.name = name;
                Categories.put(newId, cat);
                return newId;
            }
        } catch (Exception ex) { warn("Failed to insert category: " + ex.getMessage()); }
        return 0;
    }

    private boolean deleteCategoryFromDb(String name) {
        try (var c = Database.get()) {
            PreparedStatement check = c.prepareStatement("""
                SELECT COUNT(*) AS cnt FROM Question_Categories qc
                JOIN Categories c2 ON qc.category_id = c2.id
                WHERE LOWER(c2.name) = LOWER(?)
            """);
            check.setString(1, name);
            ResultSet crs = check.executeQuery();
            if (crs.next() && crs.getInt("cnt") > 0) {
                warn("Category '" + name + "' is used by some questions and cannot be deleted.");
                return false;
            }

            PreparedStatement del = c.prepareStatement("DELETE FROM Categories WHERE LOWER(name)=LOWER(?)");
            del.setString(1, name);
            int rows = del.executeUpdate();
            if (rows > 0) {
                Categories.values().removeIf(cat -> cat.name.equalsIgnoreCase(name));
                return true;
            }
        } catch (Exception ex) {
            warn("Failed to delete category: " + ex.getMessage());
        }
        return false;
    }

    // ===== Utils =====
    private String opt(String s){ return s==null?"":s.trim(); }
    private void info(String msg){ alert(Alert.AlertType.INFORMATION, msg); }
    private void warn(String msg){ alert(Alert.AlertType.WARNING, msg); }
    private void alert(Alert.AlertType t, String msg){
        Alert a=new Alert(t,msg,ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void main(String[] args){ launch(args); }
}
