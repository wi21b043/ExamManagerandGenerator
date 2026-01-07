# User Manual – Exam Manager & Generator

## System Overview

This system allows teachers to manage a question bank and generate customized exams. It supports question creation, category management, difficulty filtering, and exam export as PDF files.

---

## Main Features

### 1. Add Questions
- Users can add a new question by clicking the **“Add Question”** button.
- Input fields include:
  - Question text  
  - Difficulty (Easy / Medium / Hard)  
  - Answer  
  - Categories (multi-selection available)
- Questions can belong to one or more categories.

### 2. Delete Question
- Users can delete a question by selecting it from the question list and clicking the **“Delete Selected”** button.

### 3. Add Categories
- Click the **“+” (plus)** button near the category panel to create a new category.
- Users can define custom categories such as “Java”, “C”, “SQL”, etc.
- Once added, categories can be assigned to questions.

### 4. Delete Category
- Select the category you want to delete from the list.
- Click the **“−” (minus)** button next to the “+” button.

### 5. Load Questions
- Users can browse all saved questions.
- Each question displays:
  - Text, difficulty, categories, and versions (with left click).
- Questions can be selected for editing or deletion.

### 6. Search Questions
- Users can search and filter questions by keyword.

### 7. Generate Exam
- Users can generate a new exam in multiple steps:
  1. Enter exam title.
  2. Select one or more categories.
  3. Set how many questions to pull from each category based on difficulty (Easy / Medium / Hard).
  4. The system will randomly select questions from the database based on the settings.
  5. Users can click **Replace** to swap out any question. The replaced question will also be randomly chosen, but will match the same category and difficulty level as the original.

### 8. Save Exam to Custom Location
- After generating the exam, the system will prompt users to select a destination folder.
- The exam PDF file can be saved to any desired location on the user’s computer.

### 9. Version History
- If enabled, each time a question is edited, a version is saved.
- Users can view the version history of a question by **left-clicking** on the selected question.
- Users can also **revert (reroll)** a question to any of its previous versions with one click.

---

## How to Run the System

1. Make sure you have Java 21 and Maven installed.
2. Clone the project from Azure DevOps or your local Git repo.
3. In terminal or IntelliJ:

```bash
mvn javafx:run
```

4. The UI will open, and you can begin using the system.

---

## System Requirements

- Java 21+
- Maven
- JavaFX
- SQLite JDBC Driver (included)
