package at.technikum;

import javafx.application.Application;

public class App {
    public static void main(String[] args) {

        Database.printTables();


        Application.launch(UiApp.class, args);
    }
}
