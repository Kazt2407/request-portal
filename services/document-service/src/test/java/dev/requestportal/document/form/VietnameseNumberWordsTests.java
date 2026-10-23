package dev.requestportal.document.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VietnameseNumberWordsTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            0                 | Không
            5                 | Năm
            10                | Mười
            15                | Mười lăm
            21                | Hai mươi mốt
            24                | Hai mươi tư
            25                | Hai mươi lăm
            105               | Một trăm linh năm
            110               | Một trăm mười
            1005              | Một nghìn không trăm linh năm
            45000             | Bốn mươi lăm nghìn
            1000010           | Một triệu không trăm mười
            2500000           | Hai triệu năm trăm nghìn
            15150000          | Mười lăm triệu một trăm năm mươi nghìn
            1000000000        | Một tỷ
            1200000000000     | Một nghìn hai trăm tỷ
            1000000005        | Một tỷ không trăm linh năm
            999999999999999   | Chín trăm chín mươi chín nghìn chín trăm chín mươi chín tỷ chín trăm chín mươi chín triệu chín trăm chín mươi chín nghìn chín trăm chín mươi chín
            """)
    void readsNumbersTheVietnameseWay(long number, String words) {
        assertThat(VietnameseNumberWords.toWords(number)).isEqualTo(words);
    }

    @ParameterizedTest
    @CsvSource({"-1", "1000000000000000"})
    void rejectsNumbersOutsideTheRange(long number) {
        assertThatThrownBy(() -> VietnameseNumberWords.toWords(number)).isInstanceOf(IllegalArgumentException.class);
    }
}
