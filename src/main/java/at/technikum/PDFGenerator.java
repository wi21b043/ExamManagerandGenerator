package at.technikum;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFGenerator {

    /**
     * 生成试卷 PDF，并让用户选择保存路径
     * @param owner 当前窗口（UiApp 中传入 stage）
     * @param examName 试卷名称
     * @param lines 题目文本
     */
    public static void generate(Window owner, String examName, List<String> lines) throws IOException {

        // 1️⃣ 让用户选择保存位置
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Exam PDF");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        fileChooser.setInitialFileName("Exam_" + examName.replaceAll("\\s+", "_") + ".pdf");

        File file = fileChooser.showSaveDialog(owner);
        if (file == null) {
            System.out.println("⚠️ User canceled save dialog.");
            return;
        }

        // 2️⃣ 生成 PDF 文档
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float yStart = PDRectangle.A4.getHeight() - margin;
            float width = PDRectangle.A4.getWidth() - 2 * margin;
            float fontSize = 12f;
            float leading = 1.2f * fontSize;

            PDType1Font font = PDType1Font.HELVETICA;
            PDPageContentStream content = new PDPageContentStream(document, page);
            content.beginText();
            content.setFont(font, fontSize);
            content.setLeading(leading);
            content.newLineAtOffset(margin, yStart);

            float curY = yStart;

            // 写标题
            List<String> titleLines = wrapText("Exam: " + safeText(examName), font, fontSize, width);
            for (String t : titleLines) {
                content.showText(t);
                content.newLine();
                curY -= leading;
                if (curY <= margin) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.setLeading(leading);
                    content.newLineAtOffset(margin, yStart);
                    curY = yStart;
                }
            }

            // 空一行
            content.newLine(); curY -= leading;

            for (String raw : lines) {
                String[] parts = raw.split("\r?\n");
                for (String part : parts) {
                    List<String> wrapped = wrapText(safeText(part), font, fontSize, width);
                    for (String wl : wrapped) {
                        content.showText(wl);
                        content.newLine();
                        curY -= leading;
                        if (curY <= margin) {
                            content.endText();
                            content.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            content = new PDPageContentStream(document, page);
                            content.beginText();
                            content.setFont(font, fontSize);
                            content.setLeading(leading);
                            content.newLineAtOffset(margin, yStart);
                            curY = yStart;
                        }
                    }
                }
                // 每题后空一行
                content.newLine(); curY -= leading;
                if (curY <= margin) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.setLeading(leading);
                    content.newLineAtOffset(margin, yStart);
                    curY = yStart;
                }
            }

            content.endText();
            content.close();

            // 保存到用户选择的路径
            try (FileOutputStream out = new FileOutputStream(file)) {
                document.save(out);
            }

            System.out.println("✅ PDF saved at: " + file.getAbsolutePath());
        }
    }

    // 安全文本处理
    private static String safeText(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    }

    // 自动换行函数
    private static List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) { result.add(""); return result; }

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String candidate = line.length() == 0 ? w : line + " " + w;
            float size = font.getStringWidth(candidate) / 1000 * fontSize;
            if (size <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() == 0) {
                    result.addAll(breakLongWord(w, font, fontSize, maxWidth));
                } else {
                    result.add(line.toString());
                    line.setLength(0);
                    line.append(w);
                }
            }
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    private static List<String> breakLongWord(String w, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : w.toCharArray()) {
            cur.append(c);
            float size = font.getStringWidth(cur.toString()) / 1000 * fontSize;
            if (size > maxWidth) {
                cur.setLength(cur.length() - 1);
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
