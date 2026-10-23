package dev.requestportal.document.form;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/** Builds .docx files for tests, splitting text into small runs the way Word often does. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /**
     * @param header    text for the page header
     * @param tableCell text for a one-cell table
     * @param lines     body paragraphs, each split into runs of {@code runLength} characters
     */
    public static byte[] docx(String header, String tableCell, int runLength, String... lines) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (header != null) {
                document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText(header);
            }
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                for (int i = 0; i < line.length(); i += runLength) {
                    var run = paragraph.createRun();
                    run.setText(line.substring(i, Math.min(line.length(), i + runLength)));
                    run.setBold(i == 0);
                }
            }
            if (tableCell != null) {
                XWPFTable table = document.createTable(1, 1);
                table.getRow(0).getCell(0).getParagraphs().getFirst().createRun().setText(tableCell);
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** All text of a document: header, body paragraphs, then table cells, one entry per paragraph. */
    public static List<String> text(byte[] docx) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<String> text = new ArrayList<>();
            document.getHeaderList().forEach(header -> header.getParagraphs().forEach(p -> text.add(p.getText())));
            document.getParagraphs().forEach(p -> text.add(p.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                    .forEach(cell -> cell.getParagraphs().forEach(p -> text.add(p.getText())))));
            return text;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Whether the first run of the first body paragraph is bold, to check formatting survives filling. */
    public static boolean firstRunBold(byte[] docx) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return document.getParagraphs().getFirst().getRuns().getFirst().isBold();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
