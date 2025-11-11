User Stories – Exam Manager and Generator

### Main Role: Teacher

### Secondary Roles: Developer / Admin

### Prepared by: Team A, B, C, D

### Date: [Insert date here]

---

##  User Story Table

| ID        | As a...           | I want to...                                                                             | So that...                                             | Priority | Status      |
| --------- | ----------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------ | -------- | ----------- |
| **US-01** | Teacher           | create and manage my own categories (e.g., by topic, type, or difficulty)                | I can organize questions flexibly                      | High     | Planned     |
| **US-02** | Teacher           | store exam questions in a database                                                       | I can easily reuse and manage them later               | High     | Planned     |
| **US-03** | Teacher           | assign one question to multiple categories                                               | I can reuse it across different exams                  | Medium   | Planned     |
| **US-04** | Teacher           | automatically generate an exam by defining how many questions to take from each category | I can quickly prepare balanced exams                   | High     | Planned     |
| **US-05** | Teacher           | manually replace or edit questions in the generated exam                                 | I can fine-tune the final test                         | Medium   | Planned     |
| **US-06** | Teacher           | edit existing questions or add new ones                                                  | the question pool remains up to date                   | High     | Planned     |
| **US-07** | Teacher           | keep a version history of each question                                                  | previous versions are not lost after changes           | Medium   | Planned     |
| **US-08** | Teacher           | export the generated exam as a file (PDF, Word, or HTML)                                 | I can print or share it easily                         | High     | Planned     |
| **US-09** | Teacher           | preview the exam layout inside the GUI before exporting                                  | I can verify question order and layout                 | Medium   | Planned     |
| **US-10** | Teacher           | share the question pool with other teachers                                              | we can collaborate and reuse shared content            | Low      | Optional    |
| **US-11** | Teacher           | exclude answers and grading schemes from exported exams                                  | only the questions appear in the student version       | High     | Planned     |
| **US-12** | Developer / Admin | ensure the system runs locally without server maintenance                                | teachers can use it easily without setup overhead      | High     | Planned     |
| **US-13** | Project Team      | focus on building a small but stable prototype first                                     | reliability is ensured before adding advanced features | High     | In Progress |

---

## Notes & Constraints

- **Random logic:** Teacher defines how many questions per category to select.
- **Categories:** Created and customized by users.
- **Versioning:** Question history should be preserved, not overwritten.
- **Output:** Export format (PDF/Word/HTML) to be confirmed with supervisor.
- **Scope:** Focus on stable prototype before expanding functionality.
- **Collaboration:** Later integration with Microsoft Azure (when available).
