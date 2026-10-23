package dev.requestportal.document.form;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * {@code ${placeholder}} templates in Word documents. Word often splits a placeholder over several runs (spell
 * check, edits, formatting), so matching happens on each paragraph's combined text and only the runs a
 * placeholder spans are rewritten; the replacement takes the formatting of the run where it starts. Body,
 * tables (also nested), headers and footers are covered.
 */
public final class DocxTemplate {

    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z][a-z0-9_]*)}");

    private DocxTemplate() {
    }

    /** The placeholder names used in the document. */
    public static Set<String> placeholders(byte[] docx) {
        Set<String> names = new TreeSet<>();
        try (XWPFDocument document = open(docx)) {
            forEachParagraph(document, paragraph -> {
                Matcher matcher = PLACEHOLDER.matcher(textOf(paragraph.getRuns()));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return names;
    }

    /** Replaces every placeholder with its value; placeholders without a value become empty. */
    public static byte[] fill(byte[] docx, Map<String, String> values) {
        try (XWPFDocument document = open(docx); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            forEachParagraph(document, paragraph -> fillParagraph(paragraph, values));
            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static XWPFDocument open(byte[] docx) throws IOException {
        try {
            return new XWPFDocument(new ByteArrayInputStream(docx));
        } catch (RuntimeException exception) {
            throw new IOException("Not a valid .docx document", exception);
        }
    }

    private static void fillParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        List<String> texts = new ArrayList<>();
        for (XWPFRun run : runs) {
            texts.add(run.text());
        }
        String combined = String.join("", texts);
        Matcher matcher = PLACEHOLDER.matcher(combined);
        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[] {matcher.start(), matcher.end()});
        }
        // Right to left, so earlier offsets stay valid while later runs change.
        for (int m = matches.size() - 1; m >= 0; m--) {
            int start = matches.get(m)[0];
            int end = matches.get(m)[1];
            String name = combined.substring(start + 2, end - 1);
            String value = values.getOrDefault(name, "").replace('\n', ' ');

            int[] first = locate(texts, start);
            int[] last = locate(texts, end - 1);
            String firstText = texts.get(first[0]);
            String lastText = texts.get(last[0]);
            if (first[0] == last[0]) {
                texts.set(first[0], firstText.substring(0, first[1]) + value + firstText.substring(last[1] + 1));
            } else {
                texts.set(first[0], firstText.substring(0, first[1]) + value);
                for (int i = first[0] + 1; i < last[0]; i++) {
                    texts.set(i, "");
                }
                texts.set(last[0], lastText.substring(last[1] + 1));
            }
        }
        if (!matches.isEmpty()) {
            for (int i = 0; i < runs.size(); i++) {
                runs.get(i).setText(texts.get(i), 0);
            }
        }
    }

    /** Run index and offset within that run of a position in the paragraph's combined text. */
    private static int[] locate(List<String> texts, int position) {
        int offset = position;
        for (int i = 0; i < texts.size(); i++) {
            if (offset < texts.get(i).length()) {
                return new int[] {i, offset};
            }
            offset -= texts.get(i).length();
        }
        throw new IllegalStateException("Position " + position + " is outside the paragraph");
    }

    private static String textOf(List<XWPFRun> runs) {
        StringBuilder text = new StringBuilder();
        runs.forEach(run -> text.append(run.text()));
        return text.toString();
    }

    private static void forEachParagraph(XWPFDocument document, Consumer<XWPFParagraph> action) {
        visit(document, action);
        document.getHeaderList().forEach(header -> visit(header, action));
        document.getFooterList().forEach(footer -> visit(footer, action));
    }

    private static void visit(IBody body, Consumer<XWPFParagraph> action) {
        body.getParagraphs().forEach(action);
        for (XWPFTable table : body.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    visit(cell, action);
                }
            }
        }
    }
}
