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
import javafx.scene.input.MouseButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;



import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;
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
    private static final int ROWS_PER_PAGE = 20;

    private final ObservableList<QuestionRow> masterData = FXCollections.observableArrayList();
    private final SortedList<QuestionRow> sortedMaster = new SortedList<>(masterData);

    private Pagination pagination;


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
                    List<String> selectedCategories = showCategorySelectionDialog(stage);
                    if (selectedCategories.isEmpty()) {
                        warn("No categories selected.");
                    } else {
                        openDifficultySelectionDialog(stage, name, selectedCategories);
                    }
                }
            });
        });

        TextField tfSearch = new TextField();
        tfSearch.setPromptText("Search question...");
        tfSearch.setPrefWidth(200);

        Button btnSearch = new Button("Search");
        Button btnReset  = new Button("Reset Search");

        btnSearch.setOnAction(e -> searchQuestions(tfSearch.getText()));
        tfSearch.setOnAction(e -> searchQuestions(tfSearch.getText())); // 支持按回车搜索
        btnReset.setOnAction(e -> {
            tfSearch.clear();
            loadFromDatabase(); // 恢复完整题库
        });

        HBox top = new HBox(10, btnLoad, btnAdd, btnEdit, btnDel, btnGenExam, tfSearch, btnSearch, btnReset);
        top.setPadding(new Insets(10));
        top.setAlignment(Pos.CENTER_LEFT);

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

        // === 添加右键菜单 ===
        MenuItem viewVersions = new MenuItem("View Versions");
        viewVersions.setOnAction(e -> showVersionHistory());

        ContextMenu contextMenu = new ContextMenu(viewVersions);
        table.setContextMenu(contextMenu);


        // 让排序作用于全量数据（不是只排序当前页）
        sortedMaster.comparatorProperty().bind(table.comparatorProperty());

// 分页控件
        pagination = new Pagination(1, 0);
        pagination.setMaxPageIndicatorCount(10);

// ⭐关键：pageFactory 不要再 return table！只更新 data，然后返回一个空节点
        pagination.setPageFactory(pageIndex -> {
            updatePage(pageIndex);
            return new Pane();
        });

// 布局：table 在中间，pagination 在底部（永远不要把 table 放进 pagination）
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(table);
        root.setBottom(pagination);

        BorderPane.setMargin(table, new Insets(10));
        BorderPane.setMargin(pagination, new Insets(0, 10, 10, 10));

        stage.setScene(new Scene(root, 1080, 560));
        stage.show();


    }




    private void updatePage(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, sortedMaster.size());

        if (fromIndex >= sortedMaster.size()) {
            data.clear();
        } else {
            data.setAll(sortedMaster.subList(fromIndex, toIndex));
        }
    }


    private void refreshPagination() {
        int total = masterData.size();
        int pageCount = Math.max(1, (int) Math.ceil(total / (double) ROWS_PER_PAGE));

        pagination.setPageCount(pageCount);
        pagination.setCurrentPageIndex(0);

        // 刷新第一页
        updatePage(0);
    }





    // ===== Load data from DB (分页版：全量进 masterData，然后 refreshPagination) =====
    private void loadFromDatabase() {
        // ❗不要清 data（data 只是当前页显示用）
        masterData.clear();
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
                masterData.add(new QuestionRow(q.id, q.difficulty, q.text, cats));
            }

            // ✅关键：重算页数，并显示第 1 页（10 条）
            refreshPagination();

            info("Loaded " + questions.size() + " questions from database.");
        } catch (Exception ex) {
            warn("Failed to load from DB: " + ex.getMessage());
        }
    }

    // ===== Add/Edit Question =====
    private final Set<String> selectedCats = new HashSet<>();

    private void openAddOrEditDialog(Stage owner, QuestionRow existing) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle(existing == null ? "Add Question" : "Edit Question");

        // === 问题文本 ===
        TextArea taText = new TextArea(existing == null ? "" : existing.getText());
        taText.setPrefRowCount(6);

        // === 难度选择 ===
        ComboBox<String> cbDiff = new ComboBox<>(FXCollections.observableArrayList("Easy", "Medium", "Hard"));
        cbDiff.getSelectionModel().select(existing == null ? 0 :
                switch (existing.getDifficulty()) {
                    case "Medium" -> 1;
                    case "Hard" -> 2;
                    default -> 0;
                });

        // === 分类选择 ===
        ObservableList<String> allCats = FXCollections.observableArrayList(
                Categories.values().stream().map(c -> c.name).sorted().toList());
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

        selectedCats.clear();
        if (existing != null) selectedCats.addAll(existing.getCategories());

        // === 分类新增/删除按钮 ===
        TextField tfNewCat = new TextField();
        tfNewCat.setPromptText("Add new category");
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
            if (selected == null) {
                warn("Select a category to delete.");
                return;
            }
            if (deleteCategoryFromDb(selected)) {
                allCats.remove(selected);
                selectedCats.remove(selected);
                info("Category deleted: " + selected);
            }
        });

        Label lblHint = new Label("Select one or more categories below:");
        lblHint.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // === 新增：答案输入框 ===
        TextArea taAnswer = new TextArea();
        taAnswer.setPromptText("Enter answer here...");
        taAnswer.setPrefRowCount(3);

        // 如果是编辑模式，加载已有答案
        if (existing != null) {
            try (var c = Database.get()) {
                String ans = store.findAnswer(c, existing.getId());
                taAnswer.setText(ans);
            } catch (Exception ex) {
                warn("Failed to load answer: " + ex.getMessage());
            }
        }

        // === 布局 ===
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Difficulty:"), cbDiff);
        gp.add(new Label("Text:"), 0, 1);
        gp.add(taText, 1, 1);
        gp.add(lblHint, 1, 2);
        gp.add(lvCats, 1, 3);
        gp.add(new HBox(6, tfNewCat, btnAddCat, btnDelCat), 1, 4);

        // === 插入答案输入框 ===
        gp.add(new Label("Answer:"), 0, 5);
        gp.add(taAnswer, 1, 5);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return false;
            String text = opt(taText.getText());
            if (text.isEmpty()) {
                warn("Text required.");
                return false;
            }
            String diff = cbDiff.getValue();

            List<Integer> catIds = new ArrayList<>();
            for (String n : selectedCats)
                if (!n.isBlank()) catIds.add(ensureCategory(n.trim()));

            String answer = taAnswer.getText().trim();

            if (existing == null) {
                try (var c = Database.get()) {
                    int qid = store.insert(c, text, "short", diff);
                    for (int cid : catIds) store.linkQuestionCategory(c, qid, cid);
                    if (!answer.isEmpty()) store.insertAnswer(c, qid, answer);
                    info("Question and answer saved (ID: " + qid + ")");
                } catch (Exception ex) {
                    warn("Failed to save: " + ex.getMessage());
                }
            } else {
                try (var c = Database.get()) {
                    store.update(c, existing.getId(), text, "short", diff);
                    store.deleteQuestionCategories(c, existing.getId());
                    for (int cid : catIds) store.linkQuestionCategory(c, existing.getId(), cid);
                    store.updateAnswer(c, existing.getId(), answer);
                    info("Question and answer updated (ID: " + existing.getId() + ")");
                } catch (Exception ex) {
                    warn("Failed to update: " + ex.getMessage());
                }
            }
            loadFromDatabase();
            return true;
        });
        dlg.showAndWait();
    }


    private void deleteSelected() {
        QuestionRow sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("Select a row first.");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete question #" + sel.getId() + "?\n" +
                        "This will also remove related versions, links, and answers.",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        confirm.setHeaderText("Confirm Deletion");
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection c = Database.get()) {
                // 使用统一事务删除逻辑
                boolean ok = store.deleteQuestion(c, sel.getId());
                if (ok) {
                    loadFromDatabase();
                    info("Question deleted successfully (ID: " + sel.getId() + ").");
                } else {
                    warn("Question not found or already deleted.");
                }
            } catch (SQLException ex) {
                warn("Database error while deleting: " + ex.getMessage());
                ex.printStackTrace();
            } catch (Exception ex) {
                warn("Unexpected error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
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
        ButtonType backButtonType = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
        dialog.getDialogPane().getButtonTypes().add(backButtonType);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
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
                List<String> newSelection = showCategorySelectionDialog(owner);
                if (!newSelection.isEmpty()) {
                    openDifficultySelectionDialog(owner, examName, newSelection);
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    // ===== Exam Preview + Replace + Export =====
    private static class ExamQuestionItem {
        int id;
        String text;
        String difficulty;
        String category;

        ExamQuestionItem(int id, String text, String diff, String cat) {
            this.id = id;
            this.text = text;
            this.difficulty = diff;
            this.category = cat;
        }

        @Override
        public String toString() {
            return "[" + difficulty + "] " + text;
        }
    }

    private void generateAndExportExam(String examName, Map<String, Map<String, Integer>> selections) {
        List<ExamQuestionItem> previewList = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>(); // ✅全卷去重

        try (Connection conn = Database.get()) {
            for (var entry : selections.entrySet()) {
                String catName = entry.getKey();
                int catId = Categories.values().stream()
                        .filter(c -> c.name.equals(catName))
                        .map(c -> c.id)
                        .findFirst().orElse(-1);
                if (catId == -1) continue;

                for (var diff : List.of("Easy", "Medium", "Hard")) {
                    int count = entry.getValue().getOrDefault(diff, 0);
                    if (count <= 0) continue;

                    // ✅抓多一点候选，避免因为 usedIds 排除后不够
                    int fetch = count * 5;

                    StringBuilder sql = new StringBuilder();
                    sql.append("""
                    SELECT q.id, q.text, q.difficulty
                    FROM Questions q
                    JOIN Question_Categories qc ON q.id = qc.question_id
                    WHERE qc.category_id = ? AND q.difficulty = ?
                """);

                    // ✅排除已抽过的题
                    if (!usedIds.isEmpty()) {
                        sql.append(" AND q.id NOT IN (");
                        sql.append(String.join(",", Collections.nCopies(usedIds.size(), "?")));
                        sql.append(") ");
                    }

                    sql.append(" ORDER BY RANDOM() LIMIT ?");

                    PreparedStatement ps = conn.prepareStatement(sql.toString());
                    int idx = 1;
                    ps.setInt(idx++, catId);
                    ps.setString(idx++, diff);

                    if (!usedIds.isEmpty()) {
                        for (Integer id : usedIds) ps.setInt(idx++, id);
                    }

                    ps.setInt(idx, fetch);

                    ResultSet rs = ps.executeQuery();

                    int added = 0;
                    while (rs.next() && added < count) {
                        int qid = rs.getInt("id");
                        if (usedIds.contains(qid)) continue; // 双保险

                        previewList.add(new ExamQuestionItem(
                                qid,
                                rs.getString("text"),
                                rs.getString("difficulty"),
                                catName
                        ));
                        usedIds.add(qid);
                        added++;
                    }

                    // 不够题就提示，但不使用重复题凑数
                    if (added < count) {
                        warn("Not enough unique questions for [" + catName + "] " + diff +
                                ": requested " + count + ", got " + added + ".");
                    }
                }
            }
        } catch (Exception ex) {
            warn("Error generating preview: " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        if (previewList.isEmpty()) {
            warn("No questions found for selected parameters.");
            return;
        }

        showExamPreview(new Stage(), examName, previewList);
    }


    private void showExamPreview(Stage owner, String examName, List<ExamQuestionItem> examQuestions) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Preview Exam: " + examName);

        // === 题目列表 ===
        ListView<ExamQuestionItem> listView = new ListView<>();
        listView.setCellFactory(param -> new ListCell<>() {
            private final Button btnReplace = new Button("Replace");
            private final Label lblText = new Label();
            private final HBox hbox = new HBox(10, lblText, btnReplace);

            {
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setPadding(new Insets(5));
                lblText.setWrapText(true);
                btnReplace.setOnAction(e -> {
                    ExamQuestionItem item = getItem();
                    if (item != null) {
                        replaceQuestion(item, listView);
                    }
                });
            }

            @Override
            protected void updateItem(ExamQuestionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblText.setText(item.toString() + "  (" + item.category + ")");
                    setGraphic(hbox);
                }
            }
        });
        listView.getItems().addAll(examQuestions);

        // === 导出选项 ===
        CheckBox includeAnswers = new CheckBox("Include Answers (Teacher Version)");

        // === 按钮 ===
        Button btnExport = new Button("Export to PDF");
        Button btnCancel = new Button("Cancel");

        btnExport.setOnAction(e -> {
            try (Connection c = Database.get()) {
                List<String> lines = new ArrayList<>();
                int pos = 1;

                for (ExamQuestionItem item : listView.getItems()) {
                    String questionText = pos++ + ". " + item.text;

                    // 如果选中“包含答案”，则查询数据库并附加答案
                    if (includeAnswers.isSelected()) {
                        String ans = store.findAnswer(c, item.id);
                        if (ans != null && !ans.isBlank()) {
                            questionText += "\nAnswer: " + ans;
                        }
                    }

                    lines.add(questionText);
                }

                PDFGenerator.generate(stage, examName, lines);
                info("Exam exported successfully.");
                stage.close();
            } catch (Exception ex) {
                warn("Failed to export PDF: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        btnCancel.setOnAction(e -> stage.close());

        // === 底部布局 ===
        HBox bottom = new HBox(10, includeAnswers, btnExport, btnCancel);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10));

        // === 主界面布局 ===
        BorderPane root = new BorderPane();
        root.setCenter(listView);
        root.setBottom(bottom);
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 800, 500));
        stage.show();
    }


    private void replaceQuestion(ExamQuestionItem item, ListView<ExamQuestionItem> listView) {
        try (Connection conn = Database.get()) {
            int catId = Categories.values().stream()
                    .filter(c -> c.name.equals(item.category))
                    .map(c -> c.id)
                    .findFirst().orElse(-1);
            if (catId == -1) return;

            // ✅卷子里已存在的所有题 id（包含当前这题）
            Set<Integer> used = listView.getItems().stream()
                    .map(i -> i.id)
                    .collect(Collectors.toSet());

            StringBuilder sql = new StringBuilder();
            sql.append("""
            SELECT q.id, q.text, q.difficulty
            FROM Questions q
            JOIN Question_Categories qc ON q.id = qc.question_id
            WHERE qc.category_id = ? AND q.difficulty = ?
        """);

            // ✅排除卷子里已经出现过的题（避免替换成重复题）
            if (!used.isEmpty()) {
                sql.append(" AND q.id NOT IN (");
                sql.append(String.join(",", Collections.nCopies(used.size(), "?")));
                sql.append(") ");
            }

            sql.append(" ORDER BY RANDOM() LIMIT 1");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            int idx = 1;
            ps.setInt(idx++, catId);
            ps.setString(idx++, item.difficulty);

            for (Integer id : used) {
                ps.setInt(idx++, id);
            }

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ExamQuestionItem newQ = new ExamQuestionItem(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getString("difficulty"),
                        item.category
                );
                int pos = listView.getItems().indexOf(item);
                listView.getItems().set(pos, newQ);
                info("Question replaced successfully.");
            } else {
                warn("No alternative question found (all unique questions may already be used).");
            }
        } catch (Exception ex) {
            warn("Error replacing question: " + ex.getMessage());
            ex.printStackTrace();
        }
    }



    // 回滚按钮：选中一行 → 回滚 → 刷新历史和主表
    private void onRollbackVersion(QuestionStore store, int questionId, TableView<QuestionStore.QuestionVersion> tbl) {
        var sel = tbl.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("Please select a version first.");
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Are you sure you want to roll back question ID " + questionId +
                        " to version v" + sel.getVersion() + "?\n(This will create a new version in the history.)",
                ButtonType.OK, ButtonType.CANCEL
        );
        alert.setHeaderText("Confirm Rollback");
        var res = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (res != ButtonType.OK) return;

        try (Connection c2 = Database.get()) {
            boolean ok = store.rollbackToVersion(c2, questionId, sel.getVersion());
            if (ok) {
                // 刷新历史列表
                var refreshed = store.findVersions(c2, questionId);
                tbl.setItems(FXCollections.observableArrayList(refreshed));
                // 刷新主页
                loadFromDatabase();
                info("Rolled back to v" + sel.getVersion() + " and created a new current version.");
            } else {
                warn("Rollback failed: target version not found.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            warn("Rollback error: " + ex.getMessage());
        }
    }



    // === 查看版本历史 ===
    private void showVersionHistory() {
        QuestionRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            warn("Please select a question first.");
            return;
        }

        try (Connection c = Database.get()) {
            List<QuestionStore.QuestionVersion> versions = store.findVersions(c, selected.getId());
            if (versions.isEmpty()) {
                warn("No version history found.");
                return;
            }

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Version History");
            dlg.setHeaderText("All versions for question ID " + selected.getId());

            TableView<QuestionStore.QuestionVersion> tbl = new TableView<>();
            tbl.setItems(FXCollections.observableArrayList(versions));

            TableColumn<QuestionStore.QuestionVersion, Number> colVer = new TableColumn<>("Version");
            colVer.setCellValueFactory(new PropertyValueFactory<>("version"));

            TableColumn<QuestionStore.QuestionVersion, String> colDiff = new TableColumn<>("Difficulty");
            colDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));

            TableColumn<QuestionStore.QuestionVersion, String> colCreated = new TableColumn<>("Created");
            colCreated.setCellValueFactory(new PropertyValueFactory<>("created"));

            TableColumn<QuestionStore.QuestionVersion, String> colUpdated = new TableColumn<>("Updated");
            colUpdated.setCellValueFactory(new PropertyValueFactory<>("updated"));

            TableColumn<QuestionStore.QuestionVersion, String> colText = new TableColumn<>("Text");
            colText.setCellValueFactory(new PropertyValueFactory<>("text"));
            colText.setPrefWidth(400);

            tbl.getColumns().addAll(colVer, colDiff, colCreated, colUpdated, colText);
            tbl.setPrefHeight(300);

            // 回滚按钮
            Button btnRollback = new Button("Reroll to the selected version");
            btnRollback.setOnAction(e -> onRollbackVersion(store, selected.getId(), tbl));

            // 用 VBox 把按钮和表格放一起
            VBox box = new VBox(8, btnRollback, tbl);
            box.setPadding(new Insets(10));

            dlg.getDialogPane().setContent(box);
            dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dlg.showAndWait();


        } catch (Exception ex) {
            warn("Error loading version history: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // 搜索题目（分页版：结果写入 masterData，然后 refreshPagination）
    private void searchQuestions(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            loadFromDatabase(); // 空时加载全部
            return;
        }
        keyword = keyword.trim();

        masterData.clear();

        try (var c = Database.get()) {
            var results = store.search(c, keyword);

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

            for (var q : results) {
                List<String> cats = catsByQuestion.getOrDefault(q.id, Collections.emptyList());
                masterData.add(new QuestionRow(q.id, q.difficulty, q.text, cats));
            }

            refreshPagination();
            info(results.size() + " questions found matching: \"" + keyword + "\"");
        } catch (Exception ex) {
            warn("Search failed: " + ex.getMessage());
        }
    }


}
