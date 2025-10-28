package at.technikum;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.sql.*;
/**
 * PURE UI MOCK (no JDBC/DAO) — but aligned with DB schema/flows
 * ------------------------------------------------------------
 * Matches schema names and flows in the brief without touching a real DB:
 *   Tables: Categories(name), Questions(id, text, difficulty),
 *           Question_Categories(question_id, category_id),
 *           Exams(id, name, created_at), Exam_Questions(exam_id, question_id, position)
 *
 * - UI supports: Load (demo data), Add/Edit/Delete Question, Build Exam by per-category counts
 * - In-memory structures mirror the tables above so you can later swap in DAO calls 1:1
 * - Zero external deps beyond JavaFX — should compile with only javafx-controls
 */
public class UiApp extends Application {

    private final QuestionStore store = new QuestionStore();

    // ===== In-memory structures that mirror the DB tables =====
    static class Category { int id; String name; }
    static class Question { int id; String text; String difficulty; }
    static class QuestionCategory { int question_id; int category_id; }
    static class Exam { int id; String name; String created_at; }
    static class ExamQuestion { int exam_id; int question_id; int position; }

    private final Map<Integer, Category> Categories = new LinkedHashMap<>();
    private final Map<Integer, Question> Questions = new LinkedHashMap<>();
    private final List<QuestionCategory> Question_Categories = new ArrayList<>();
    private final Map<Integer, Exam> Exams = new LinkedHashMap<>();
    private final List<ExamQuestion> Exam_Questions = new ArrayList<>();

    // simple autoincrement counters
    private final AtomicInteger catSeq = new AtomicInteger(1);
    private final AtomicInteger qSeq   = new AtomicInteger(1);
    private final AtomicInteger examSeq= new AtomicInteger(1);

    // ===== UI state =====
    public static class QuestionRow {
        private final int id; private final String difficulty; private final String text; private final List<String> categories;
        public QuestionRow(int id, String difficulty, String text, List<String> categories){this.id=id;this.difficulty=difficulty;this.text=text;this.categories=categories;}
        public int getId(){return id;} public String getDifficulty(){return difficulty;} public String getText(){return text;} public String getCategoriesCsv(){return String.join(", ", categories);} public List<String> getCategories(){return categories;}
    }

    private final TableView<QuestionRow> table = new TableView<>();
    private final ObservableList<QuestionRow> data = FXCollections.observableArrayList();

    @Override public void start(Stage stage){
        stage.setTitle("Exam Manager (Pure UI Mock, DB-aligned)");

        Button btnLoad  = new Button("Load");
        Button btnAdd   = new Button("Add Question");
        Button btnEdit  = new Button("Edit Selected");
        Button btnDel   = new Button("Delete Selected");
        Button btnBuild = new Button("Build Exam…");

        btnLoad.setOnAction(e -> loadFromDatabase());

        btnAdd.setOnAction(e -> openAddOrEditDialog(stage, null));

        btnEdit.setOnAction(e -> {
            QuestionRow sel = table.getSelectionModel().getSelectedItem();
            if (sel==null){warn("Select a row.");return;}
            openAddOrEditDialog(stage, sel);
        });
        btnDel.setOnAction(e -> deleteSelected());
        btnBuild.setOnAction(e -> openBuildDialog(stage));

        HBox top = new HBox(10, btnLoad, btnAdd, btnEdit, btnDel, btnBuild);
        top.setPadding(new Insets(10)); top.setAlignment(Pos.CENTER_LEFT);

        TableColumn<QuestionRow, Number> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id")); cId.setPrefWidth(70);
        TableColumn<QuestionRow, String> cDiff = new TableColumn<>("Difficulty");
        cDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty")); cDiff.setPrefWidth(110);
        TableColumn<QuestionRow, String> cCat = new TableColumn<>("Categories");
        cCat.setCellValueFactory(new PropertyValueFactory<>("categoriesCsv")); cCat.setPrefWidth(220);
        TableColumn<QuestionRow, String> cText = new TableColumn<>("Text");
        cText.setCellValueFactory(new PropertyValueFactory<>("text")); cText.setPrefWidth(600);
        table.getColumns().addAll(cId, cDiff, cCat, cText);
        table.setItems(data);

        BorderPane root = new BorderPane(); root.setTop(top); root.setCenter(table);
        BorderPane.setMargin(table, new Insets(10));
        stage.setScene(new Scene(root, 1080, 560)); stage.show();
    }

    private void loadFromDatabase() {
        data.clear();
        loadCategoriesFromDb();
        try (var c = Database.get()) {
            var questions = store.findAll(c);
            for (var q : questions) {
                data.add(new QuestionRow(q.id, q.difficulty, q.text, List.of("(from DB)"))); // 暂时只显示DB来源
            }
            info("Loaded " + questions.size() + " questions from database.");
        } catch (Exception ex) {
            warn("Failed to load from DB: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ===== Demo data (simulates SELECTs) =====
    private void loadDemo(){
        // reset all tables
        Categories.clear(); Questions.clear(); Question_Categories.clear(); Exams.clear(); Exam_Questions.clear();
        catSeq.set(1); qSeq.set(1); examSeq.set(1);

        // Categories(name)
        int oop = insertCategory("OOP");
        int sql = insertCategory("SQL");
        int fx  = insertCategory("JavaFX");

        // Questions(text, difficulty)
        int q1 = insertQuestion("What is encapsulation?", "Easy");
        int q2 = insertQuestion("Write a SELECT with GROUP BY.", "Medium");
        int q3 = insertQuestion("Explain Scene Graph.", "Hard");
        int q4 = insertQuestion("Difference between interface and abstract class?", "Medium");

        // Question_Categories(question_id, category_id)
        linkQC(q1, oop); linkQC(q2, sql); linkQC(q3, fx); linkQC(q4, oop);

        refreshTable();
        info("Loaded demo data into in-memory tables.");
    }

    private void refreshTable(){
        data.clear();
        for (Question q : Questions.values()){
            List<String> cats = Question_Categories.stream()
                    .filter(x -> x.question_id==q.id)
                    .map(x -> Categories.get(x.category_id).name)
                    .sorted().collect(Collectors.toList());
            data.add(new QuestionRow(q.id, q.difficulty, q.text, cats));
        }
        data.sort(Comparator.comparingInt(QuestionRow::getId));
    }

    // ===== Add/Edit/Delete Question =====
    private void openAddOrEditDialog(Stage owner, QuestionRow existing){
        Dialog<Boolean> dlg = new Dialog<>(); dlg.initOwner(owner); dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle(existing==null?"Add Question":"Edit Question");

        TextArea taText = new TextArea(existing==null?"":existing.getText()); taText.setPrefRowCount(6);
        ComboBox<String> cbDiff = new ComboBox<>(FXCollections.observableArrayList("Easy","Medium","Hard"));
        cbDiff.getSelectionModel().select(existing==null?0: switch(existing.getDifficulty()){case "Medium"->1; case "Hard"->2; default->0;});

        ListView<String> lvCats = new ListView<>(); lvCats.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ObservableList<String> allCats = FXCollections.observableArrayList(Categories.values().stream().map(c->c.name).sorted().toList());
        lvCats.setItems(allCats);
        if (existing!=null){ for (String n: existing.getCategories()) lvCats.getSelectionModel().select(n); }

        TextField tfNewCat = new TextField(); tfNewCat.setPromptText("Add new category then press +");
        //Button btnAddCat = new Button("+"); btnAddCat.setOnAction(e->{ String n = opt(tfNewCat.getText()); if(!n.isEmpty() && !allCats.contains(n)){ allCats.add(n); FXCollections.sort(allCats); } tfNewCat.clear(); });
        Button btnAddCat = new Button("+");
        btnAddCat.setOnAction(e -> {
            String n = opt(tfNewCat.getText());
            if (!n.isEmpty() && !allCats.contains(n)) {
                // 调用数据库插入方法
                int id = insertCategoryIntoDb(n);
                if (id > 0) {
                    allCats.add(n);
                    FXCollections.sort(allCats);
                }
            }
            tfNewCat.clear();
        });
        GridPane gp = new GridPane(); gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Difficulty:"), cbDiff);
        gp.add(new Label("Text:"),0,1); gp.add(taText,1,1);
        gp.add(new Label("Categories:"),0,2); gp.add(lvCats,1,2);
        gp.add(new HBox(6, tfNewCat, btnAddCat),1,3);

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt->{
            if (bt!=ButtonType.OK) return false;
            String text = opt(taText.getText()); if (text.isEmpty()){ warn("Text required."); return false; }
            String diff = cbDiff.getValue();

            List<String> selected = new ArrayList<>(lvCats.getSelectionModel().getSelectedItems());
            // ensure categories exist (mirrors INSERT OR IGNORE on Categories)
            List<Integer> catIds = new ArrayList<>();
            for (String n : selected){ if (!n.isBlank()) catIds.add(ensureCategory(n.trim())); }

//            if (existing==null){
//                int qid = insertQuestion(text, diff);
//                for (int cid: catIds) linkQC(qid, cid);
//            } else {
//                // update question
//                Question q = Questions.get(existing.getId()); q.text=text; q.difficulty=diff;
//                // relink
//                Question_Categories.removeIf(x->x.question_id==q.id);
//                for (int cid: catIds) linkQC(q.id, cid);
//            }
            if (existing == null) {
                try (var c = Database.get()) {
                    int qid = store.insert(c, text, "short", diff); // 先用 type="short" 或按你实际UI字段
                    info("Question saved to database (ID: " + qid + ")");
                } catch (Exception ex) {
                    warn("Failed to save question: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                // 以后可添加“update”功能
                    try (var c = Database.get()) {
                        store.update(c, existing.getId(), text, "short", diff);
                        info("Question updated (ID: " + existing.getId() + ")");
                        loadFromDatabase();
                    } catch (Exception ex) {
                        warn("Failed to update: " + ex.getMessage());
                    }
            }
            refreshTable();
            return true;
        });

        dlg.showAndWait();
    }

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
                    store.delete(c, sel.getId());
                    info("Question deleted (ID: " + sel.getId() + ")");
                    loadFromDatabase();
                } catch (Exception ex) {
                    warn("Failed to delete: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }


    // ===== Build Exam (random pick per category) =====
    private void openBuildDialog(Stage owner){
        if (Categories.isEmpty()){ warn("No categories."); return; }
        Dialog<Boolean> dlg = new Dialog<>(); dlg.initOwner(owner); dlg.initModality(Modality.WINDOW_MODAL); dlg.setTitle("Build Exam");

        TextField tfName = new TextField("Exam " + new java.util.Date());
        GridPane gp = new GridPane(); gp.setHgap(10); gp.setVgap(8); gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Exam name:"), tfName);
        gp.add(new Label("Per-category counts:"), 0, 1);

        List<Category> catList = new ArrayList<>(Categories.values()); catList.sort(Comparator.comparing(c->c.name));
        List<Spinner<Integer>> spinners = new ArrayList<>();
        int r=2; for (Category c : catList){ Spinner<Integer> sp = new Spinner<>(0, 1000, 0); sp.setEditable(true); spinners.add(sp); gp.addRow(r++, new Label(c.name+":"), sp); }

        dlg.getDialogPane().setContent(gp);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt->{
            if (bt!=ButtonType.OK) return false;
            String name = opt(tfName.getText()); if (name.isEmpty()){ warn("Name required."); return false; }
            Map<Integer,Integer> demandByCat = new LinkedHashMap<>();
            for (int i=0;i<catList.size();i++) demandByCat.put(catList.get(i).id, spinners.get(i).getValue());
            int total = demandByCat.values().stream().mapToInt(Integer::intValue).sum(); if (total==0){ warn("Pick at least one question."); return false; }

            // availability check
            StringBuilder insufficient = new StringBuilder();
            for (var e : demandByCat.entrySet()){
                int have = (int) Question_Categories.stream().filter(qc->qc.category_id==e.getKey()).count();
                if (have < e.getValue()) insufficient.append(Categories.get(e.getKey()).name).append(" (need ").append(e.getValue()).append(", have ").append(have).append(")\n");
            }
            if (insufficient.length()>0){ warn("Not enough questions in:\n"+insufficient); return false; }

            int examId = insertExam(name);
            Random rnd = new Random(); int pos=1;
            for (var e : demandByCat.entrySet()){
                int cid = e.getKey(), need = e.getValue(); if (need==0) continue;
                List<Integer> qids = Question_Categories.stream().filter(qc->qc.category_id==cid).map(qc->qc.question_id).distinct().collect(Collectors.toList());
                Collections.shuffle(qids, rnd);
                for (int i=0;i<need;i++) insertExamQuestion(examId, qids.get(i), pos++);
            }
            info("Exam #"+examId+" created with "+(pos-1)+" questions (in-memory only).");
            return true;
        });

        dlg.showAndWait();
    }

    // ===== In-memory helpers that mimic INSERTs =====
    private int insertCategory(String name){ int id = catSeq.getAndIncrement(); Category c = new Category(); c.id=id; c.name=name; Categories.put(id, c); return id; }
    private int ensureCategory(String name) {
        return insertCategoryIntoDb(name);
    }
    //private int ensureCategory(String name){ return Categories.values().stream().filter(c->c.name.equalsIgnoreCase(name)).map(c->c.id).findFirst().orElseGet(() -> insertCategory(name)); }
    private int insertQuestion(String text, String difficulty){ int id = qSeq.getAndIncrement(); Question q = new Question(); q.id=id; q.text=text; q.difficulty=difficulty; Questions.put(id, q); return id; }
    private void linkQC(int qid, int cid){ QuestionCategory qc = new QuestionCategory(); qc.question_id=qid; qc.category_id=cid; // avoid dup
        boolean exists = Question_Categories.stream().anyMatch(x->x.question_id==qid && x.category_id==cid);
        if (!exists) Question_Categories.add(qc);
    }
    private int insertExam(String name){ int id = examSeq.getAndIncrement(); Exam e = new Exam(); e.id=id; e.name=name; e.created_at=new java.util.Date().toString(); Exams.put(id,e); return id; }
    private void insertExamQuestion(int examId, int qid, int pos){ ExamQuestion eq = new ExamQuestion(); eq.exam_id=examId; eq.question_id=qid; eq.position=pos; Exam_Questions.add(eq);}

    // ===== Utils =====
    private String opt(String s){ return s==null?"":s.trim(); }
    private void info(String msg){ alert(Alert.AlertType.INFORMATION, msg);} private void warn(String msg){ alert(Alert.AlertType.WARNING, msg);} private void alert(Alert.AlertType t,String msg){ Alert a=new Alert(t,msg,ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    /** 从数据库加载所有类别到 Categories map */
    private void loadCategoriesFromDb() {
        Categories.clear();
        try (var c = Database.get()) {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT id, name FROM Categories ORDER BY name");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Category cat = new Category();
                cat.id = rs.getInt("id");
                cat.name = rs.getString("name");
                Categories.put(cat.id, cat);
            }
        } catch (Exception ex) {
            warn("Failed to load categories: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 向数据库插入类别（若不存在），并返回 ID。
     * 同时把新建的类别放入 Categories map。
     */
    private int insertCategoryIntoDb(String name) {
        try (var c = Database.get()) {
            // 判断是否已存在（忽略大小写）
            PreparedStatement find = c.prepareStatement(
                    "SELECT id FROM Categories WHERE LOWER(name) = LOWER(?)");
            find.setString(1, name);
            ResultSet fr = find.executeQuery();
            if (fr.next()) {
                return fr.getInt("id");
            }
            // 插入新记录
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Categories(name) VALUES(?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newId = rs.getInt(1);
                Category cat = new Category();
                cat.id = newId;
                cat.name = name;
                Categories.put(newId, cat);  // 更新内存映射
                return newId;
            }
        } catch (Exception ex) {
            warn("Failed to insert category: " + ex.getMessage());
            ex.printStackTrace();
        }
        return 0;
    }
    public static void main(String[] args){ launch(args); }
}
