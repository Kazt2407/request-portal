package dev.requestportal.document.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BulkDownloadWorkerTests {

    @Test
    void repeatedNamesAreNumberedBeforeTheExtension() {
        Map<String, Integer> used = new HashMap<>();

        assertThat(BulkDownloadWorker.uniqueName("ACAD-1/scan.pdf", used)).isEqualTo("ACAD-1/scan.pdf");
        assertThat(BulkDownloadWorker.uniqueName("ACAD-1/scan.pdf", used)).isEqualTo("ACAD-1/scan (2).pdf");
        assertThat(BulkDownloadWorker.uniqueName("ACAD-1/scan.pdf", used)).isEqualTo("ACAD-1/scan (3).pdf");
        assertThat(BulkDownloadWorker.uniqueName("ACAD-2/scan.pdf", used)).isEqualTo("ACAD-2/scan.pdf");
        assertThat(BulkDownloadWorker.uniqueName("ACAD.1/README", used)).isEqualTo("ACAD.1/README");
        assertThat(BulkDownloadWorker.uniqueName("ACAD.1/README", used)).isEqualTo("ACAD.1/README (2)");
    }
}
