package at.technikum;

import javafx.application.Application;

public class App {
    public static void main(String[] args) {

        //打印表名做健康检查（可保留）
        Database.printTables();

        //启动 JavaFX UI（你们已经有 UiApp）
        Application.launch(UiApp.class, args);
    }
}
