package dev.requestportal.request.report;

import dev.requestportal.request.workflow.Actor;
import dev.requestportal.request.workflow.RequestViews;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;

/** A printable record of one request, as a Word document: its details, values, steps and history. */
final class RequestDocument {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Text width of a page with Word's default margins, in twentieths of a point. */
    private static final int TEXT_WIDTH_TWIPS = 9360;

    private final XWPFDocument document = new XWPFDocument();
    private final ZoneId zone;

    private RequestDocument(ZoneId zone) {
        this.zone = zone;
    }

    static byte[] render(RequestViews.Detail request, ZoneId zone) {
        RequestDocument writer = new RequestDocument(zone);
        try (XWPFDocument document = writer.document; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.write(request);
            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void write(RequestViews.Detail request) {
        heading(request.title(), 16);
        line("Code", request.code());
        line("Status", words(request.status().name()));
        line("Requester", request.requester().name() + " (" + request.requester().email() + ")");
        line("Submitted", time(request.submittedAt()));
        if (request.finishedAt() != null) {
            line("Finished", time(request.finishedAt()));
        }

        heading("Details", 13);
        table(new String[] {"Field", "Value"}, new int[] {30, 70}, request.values().stream()
                .map(value -> new String[] {value.label(), text(value.value())})
                .toList());

        heading("Processing steps", 13);
        table(new String[] {"#", "Dept.", "Task", "Status", "Due", "Finished", "By", "Note"},
                new int[] {4, 9, 22, 13, 14, 14, 12, 12},
                request.steps().stream()
                        .map(step -> new String[] {String.valueOf(step.position()), step.departmentCode(),
                                step.description(), words(step.status().name()) + (step.overdue() ? " (overdue)" : ""),
                                time(step.dueAt()), time(step.finishedAt()), name(step.finishedBy()), step.note()})
                        .toList());

        heading("History", 13);
        table(new String[] {"Time", "Action", "Step", "By", "Note"}, new int[] {17, 18, 8, 22, 35},
                request.history().stream()
                .map(entry -> new String[] {time(entry.at()), words(entry.action().name()),
                        entry.stepPosition() == null ? "" : String.valueOf(entry.stepPosition()), name(entry.actor()),
                        entry.note()})
                .toList());
    }

    private void heading(String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(240);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text);
    }

    private void line(String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun bold = paragraph.createRun();
        bold.setBold(true);
        bold.setText(label + ": ");
        paragraph.createRun().setText(value);
    }

    /** A full-width table with a bold header row; {@code widths} are percentages of the page width. */
    private void table(String[] columns, int[] widths, List<String[]> rows) {
        XWPFTable table = document.createTable(rows.size() + 1, columns.length);
        table.setWidth("100%");
        // Word reads the cell widths, LibreOffice the column grid; set both.
        CTTblGrid grid = table.getCTTbl().getTblGrid() != null
                ? table.getCTTbl().getTblGrid()
                : table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() < columns.length) {
            grid.addNewGridCol();
        }
        for (int column = 0; column < columns.length; column++) {
            grid.getGridColArray(column).setW(BigInteger.valueOf(TEXT_WIDTH_TWIPS * widths[column] / 100));
            write(cell(table, 0, column), columns[column], true);
        }
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < columns.length; column++) {
                write(cell(table, row + 1, column), rows.get(row)[column], false);
            }
        }
        for (int row = 0; row <= rows.size(); row++) {
            for (int column = 0; column < columns.length; column++) {
                cell(table, row, column).setWidth(widths[column] + "%");
            }
        }
    }

    /** Tables use a smaller font so the step table's eight columns fit a portrait page. */
    private static void write(XWPFTableCell cell, String text, boolean bold) {
        XWPFRun run = cell.getParagraphs().getFirst().createRun();
        run.setBold(bold);
        run.setFontSize(9);
        run.setText(text == null ? "" : text);
    }

    private static XWPFTableCell cell(XWPFTable table, int row, int column) {
        return table.getRow(row).getCell(column);
    }

    /** File fields hold file ids; the document says how many rather than printing ids. */
    private static String text(Object value) {
        return switch (value) {
            case null -> "";
            case Collection<?> files -> files.size() == 1 ? "1 file attached" : files.size() + " files attached";
            default -> value.toString();
        };
    }

    private String time(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(zone));
    }

    private static String name(Actor actor) {
        return actor == null ? "" : actor.name();
    }

    /** IN_PROGRESS becomes "In progress". */
    private static String words(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
