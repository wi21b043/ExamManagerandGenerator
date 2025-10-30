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
import java.util.Date;
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
        Button btnGenExam = new Button("Generate Exam");

        btnLoad.setOnAction(e -> loadFromDatabase());
        btnAdd.setOnAction(e -> openAddOrEditDialog(stage, null));
        btnEdit.setOnAction(e -> {
            QuestionRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { warn("Select a row."); return; }
            openAddOrEditDialog(stage, sel);
        });
        btnDel.setOnAction(e -> deleteSelected());
        btnGenExam.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Exam " + new Date());
            dialog.setTitle("New Exam");
            dialog.setHeaderText("Create a New Exam");
            dialog.setContentText("Enter exam name:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (name.trim().isEmpty()) {
                    warn("Exam name cannot be empty.");
                } else {
                    // Category selection dialog logic
                    List<String> selectedCategories = showCategorySelectionDialog(stage);
                    if (selectedCategories.isEmpty()) {
                        warn("No categories selected.");
                    } else {
                        // info("Selected categories: " + String.join(", ", selectedCategories));
                        openDifficultySelectionDialog(stage, name, selectedCategories);
                    }
                }
            });
        });

        HBox top = new HBox(10, btnLoad, btnAdd, btnEdit, btnDel, btnGenExam);
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
    // ===== Category Selection Dialog =====
    private List<String> showCategorySelectionDialog(Stage owner) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Select Categories");
        dialog.setHeaderText("Choose categories for the exam:");

        ObservableList<String> allCats = FXCollections.observableArrayList(Categories.values().stream()
                .map(c -> c.name).sorted().toList());
        ListView<String> listView = new ListView<>(allCats);
        Set<String> selectedCatsInDialog = new HashSet<>();
        listView.setCellFactory(CheckBoxListCell.forListView(item -> {
            SimpleBooleanProperty prop = new SimpleBooleanProperty(false);
            prop.addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) selectedCatsInDialog.add(item);
                else selectedCatsInDialog.remove(item);
            });
            return prop;
        }));
        listView.setPrefHeight(200);
        dialog.getDialogPane().setContent(listView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                return new ArrayList<>(selectedCatsInDialog);
            }
            return Collections.emptyList();
        });

        Optional<List<String>> result = dialog.showAndWait();
        return result.orElse(Collections.emptyList());
    }

    private void openDifficultySelectionDialog(Stage owner, String examName, List<String> selectedCategories) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Select Difficulty Counts");
        dialog.setHeaderText("Specify number of questions per difficulty for each category:");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        Map<String, Spinner<Integer>> easySpinners = new HashMap<>();
        Map<String, Spinner<Integer>> medSpinners = new HashMap<>();
        Map<String, Spinner<Integer>> hardSpinners = new HashMap<>();

        int row = 0;
        for (String cat : selectedCategories) {
            grid.add(new Label(cat + ":"), 0, row);

            Spinner<Integer> spEasy = new Spinner<>(0, 50, 0);
            Spinner<Integer> spMed = new Spinner<>(0, 50, 0);
            Spinner<Integer> spHard = new Spinner<>(0, 50, 0);
            spEasy.setEditable(true); spMed.setEditable(true); spHard.setEditable(true);

            easySpinners.put(cat, spEasy);
            medSpinners.put(cat, spMed);
            hardSpinners.put(cat, spHard);

            grid.add(new Label("Easy:"), 1, row);
            grid.add(spEasy, 2, row);
            grid.add(new Label("Medium:"), 3, row);
            grid.add(spMed, 4, row);
            grid.add(new Label("Hard:"), 5, row);
            grid.add(spHard, 6, row);
            row++;
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Add "Back" button
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
        dialog.getDialogPane().getButtonTypes().add(backButtonType);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                // Gather selection into a nested map: category -> {difficulty -> count}
                Map<String, Map<String, Integer>> selections = new LinkedHashMap<>();
                for (String cat : selectedCategories) {
                    Map<String, Integer> diffs = new LinkedHashMap<>();
                    diffs.put("Easy", easySpinners.get(cat).getValue());
                    diffs.put("Medium", medSpinners.get(cat).getValue());
                    diffs.put("Hard", hardSpinners.get(cat).getValue());
                    selections.put(cat, diffs);
                }
                generateAndExportExam(examName, selections);
                return true;
            } else if (bt.getButtonData() == ButtonBar.ButtonData.BACK_PREVIOUS) {
                // Show category selection dialog again
                List<String> newSelection = showCategorySelectionDialog(owner);
                if (!newSelection.isEmpty()) {
                    openDifficultySelectionDialog(owner, examName, newSelection);
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    // ===== Exam Generation and Export =====
    /**
     * Generates an exam based on the given selections and exports it as a PDF.
     * @param examName The exam name.
     * @param selections Map of category name -> (difficulty -> count)
     */
    private void generateAndExportExam(String examName, Map<String, Map<String, Integer>> selections) {
        try (Connection conn = Database.get()) {
            // Insert exam (using 'title' instead of 'name' as per DB schema)
            PreparedStatement psExam = conn.prepareStatement("INSERT INTO Exams(title, created_at) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS);
            psExam.setString(1, examName);
            psExam.setString(2, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            psExam.executeUpdate();
            ResultSet rsExam = psExam.getGeneratedKeys();
            if (!rsExam.next()) { warn("Failed to insert exam."); return; }
            int examId = rsExam.getInt(1);

            List<String> lines = new ArrayList<>();
            int position = 1;

            for (var entry : selections.entrySet()) {
                String catName = entry.getKey();
                int catId = Categories.values().stream().filter(c -> c.name.equals(catName)).map(c -> c.id).findFirst().orElse(-1);
                if (catId == -1) continue;

                for (var diff : List.of("Easy", "Medium", "Hard")) {
                    int count = entry.getValue().getOrDefault(diff, 0);
                    if (count <= 0) continue;

                    // Randomly select matching questions
                    PreparedStatement ps = conn.prepareStatement("""
                        SELECT q.id, q.text FROM Questions q
                        JOIN Question_Categories qc ON q.id = qc.question_id
                        WHERE qc.category_id = ? AND q.difficulty = ?
                        ORDER BY RANDOM() LIMIT ?
                    """);
                    ps.setInt(1, catId);
                    ps.setString(2, diff);
                    ps.setInt(3, count);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int qid = rs.getInt("id");
                        String text = rs.getString("text");
                        lines.add(position + ". " + text);
                        PreparedStatement psInsert = conn.prepareStatement("INSERT INTO Exam_Questions(exam_id, question_id, order_index) VALUES (?, ?, ?)");
                        psInsert.setInt(1, examId);
                        psInsert.setInt(2, qid);
                        psInsert.setInt(3, position++);
                        psInsert.executeUpdate();
                    }
                }
            }

            if (lines.isEmpty()) {
                warn("No questions matched your selection.");
                return;
            }

            // Generate PDF

            PDFGenerator.generate(new Stage(), examName, lines);
            info("Exam created and saved as PDF (user selected path).");
        } catch (Exception ex) {
            warn("Error generating exam: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}