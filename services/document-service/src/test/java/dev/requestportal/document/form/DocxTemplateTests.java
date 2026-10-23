package dev.requestportal.document.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocxTemplateTests {

    @Test
    void findsPlaceholdersEvenWhenWordSplitThemAcrossRuns() {
        byte[] template = TestDocuments.docx("Ngày ${today}", "Email: ${email}", 3,
                "Họ và tên: ${full_name}, lớp ${class_name}", "Không có biến ở đây", "Giá ${amount} đ");

        assertThat(DocxTemplate.placeholders(template))
                .containsExactly("amount", "class_name", "email", "full_name", "today");
    }

    @Test
    void fillsBodyTablesAndHeadersAndKeepsTheSurroundingText() {
        byte[] template = TestDocuments.docx("Ngày ${today}", "Email: ${email}", 4,
                "Họ và tên: ${full_name}, lớp ${class_name}.", "${amount}${amount} liền nhau");

        byte[] filled = DocxTemplate.fill(template, Map.of(
                "today", "22/10/2026", "email", "an@student.example.edu", "full_name", "Nguyễn Văn An",
                "class_name", "DHCNTT21A", "amount", "5"));

        assertThat(TestDocuments.text(filled)).containsExactly(
                "Ngày 22/10/2026",
                "Họ và tên: Nguyễn Văn An, lớp DHCNTT21A.",
                "55 liền nhau",
                "Email: an@student.example.edu");
        assertThat(TestDocuments.firstRunBold(filled)).as("formatting of the first run is kept").isTrue();
    }

    @Test
    void placeholdersWithoutValueBecomeEmptyAndOtherDollarSignsStay() {
        byte[] template = TestDocuments.docx(null, null, 50, "Phí: $5, mã ${missing}, ${Not_A_Placeholder}");

        byte[] filled = DocxTemplate.fill(template, Map.of());

        assertThat(TestDocuments.text(filled)).containsExactly("Phí: $5, mã , ${Not_A_Placeholder}");
    }

    @Test
    void rejectsFilesThatAreNotWordDocuments() {
        assertThatThrownBy(() -> DocxTemplate.placeholders("not a docx".getBytes()))
                .isInstanceOf(UncheckedIOException.class);
    }
}
