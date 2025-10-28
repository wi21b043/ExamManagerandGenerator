package at.technikum;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFGenerator {

    // Simple PDF generator using PDFBox with safe handling of line breaks and page wrapping
    public static void generate(String examName, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float yStart = PDRectangle.A4.getHeight() - margin; // start near top
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

            // write a title
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

            // blank line
            content.newLine(); curY -= leading;

            for (String raw : lines) {
                // split incoming line on actual newline chars to be safe
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
                // add an empty line between questions for readability
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

            String safeName = examName == null || examName.isBlank() ? "exam" : examName.replaceAll("[^a-zA-Z0-9\\-_]", "_");
            File dir = new File("pdfs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, safeName + ".pdf");
            document.save(out);
        }
    }

    // Replace control chars that PDF font encoding can't handle; keep printable characters and spaces
    private static String safeText(String s) {
        if (s == null) return "";
        // remove non-printable control characters except TAB (0x09)
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    }

    // very small word-wrap helper using font metrics
    private static List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) { result.add(""); return result; }

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String candidate = line.length() == 0 ? w : line + " " + w;
            float size = font.getStringWidth(candidate) / 1000 * fontSize;
            if (size <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() == 0) {
                    // single very long word, force-break
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
                // remove last char and push
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