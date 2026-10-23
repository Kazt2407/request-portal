package dev.requestportal.request.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * A small writer over POI's streaming workbook: only a window of rows stays in memory, so large exports do
 * not grow the heap. Dates become real Excel dates in the portal's time zone.
 */
final class ExcelWorkbook implements AutoCloseable {

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(200);
    private final ZoneId zone;
    private final CellStyle header;
    private final CellStyle dateTime;
    private final CellStyle decimal;
    private Sheet sheet;
    private int rowIndex;

    ExcelWorkbook(ZoneId zone) {
        this.zone = zone;
        Font bold = workbook.createFont();
        bold.setBold(true);
        header = workbook.createCellStyle();
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
        decimal = workbook.createCellStyle();
        decimal.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
    }

    /** Starts a sheet with a bold header row; widths are in characters. */
    void sheet(String name, String[] columns, int[] widths) {
        sheet = workbook.createSheet(name);
        rowIndex = 0;
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < columns.length; i++) {
            var cell = row.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(header);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    /** Writes a row; values may be text, numbers, booleans (Yes/No), instants or null. */
    void row(Object... values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.length; i++) {
            var cell = row.createCell(i);
            switch (values[i]) {
                case null -> { }
                case String text -> cell.setCellValue(text);
                case Double number -> {
                    cell.setCellValue(number);
                    cell.setCellStyle(decimal);
                }
                case Number number -> cell.setCellValue(number.doubleValue());
                case Boolean flag -> cell.setCellValue(flag ? "Yes" : "No");
                case Instant instant -> {
                    cell.setCellValue(instant.atZone(zone).toLocalDateTime());
                    cell.setCellStyle(dateTime);
                }
                default -> cell.setCellValue(values[i].toString());
            }
        }
    }

    byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Also removes the temporary files the streaming workbook wrote. */
    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
