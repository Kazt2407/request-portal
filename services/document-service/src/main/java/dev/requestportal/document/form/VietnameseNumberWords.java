package dev.requestportal.document.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes amounts in Vietnamese words, as on receipts and forms: 2 500 000 → "Hai triệu năm trăm nghìn".
 * Follows the usual rules: "mười" for 10–19 and "mươi" after, "mốt" (21), "tư" (24), "lăm" (15, 25),
 * "linh" for a zero tens digit (105), "không trăm" inside larger numbers (1 005), and repeated "tỷ"
 * for billions (1 200 tỷ is "một nghìn hai trăm tỷ").
 */
public final class VietnameseNumberWords {

    public static final long MAX = 999_999_999_999_999L;

    private static final long BILLION = 1_000_000_000L;
    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};

    private VietnameseNumberWords() {
    }

    public static String toWords(long number) {
        if (number < 0 || number > MAX) {
            throw new IllegalArgumentException("Supported range is 0 to " + MAX + ": " + number);
        }
        if (number == 0) {
            return "Không";
        }
        String text = read(number, false);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Billions repeat rather than taking a new unit: 1 200 000 000 000 is "một nghìn hai trăm tỷ", so the part
     * above a billion is read as a number of its own followed by "tỷ".
     *
     * @param full read zero hundreds ("không trăm") because something larger came before
     */
    private static String read(long number, boolean full) {
        long billions = number / BILLION;
        long rest = number % BILLION;
        if (billions == 0) {
            return readBelowBillion(rest, full);
        }
        String words = read(billions, full) + " tỷ";
        return rest == 0 ? words : words + " " + readBelowBillion(rest, true);
    }

    private static String readBelowBillion(long number, boolean full) {
        int[] groups = {(int) (number / 1_000_000), (int) (number / 1000 % 1000), (int) (number % 1000)};
        String[] units = {"triệu", "nghìn", ""};
        List<String> words = new ArrayList<>();
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] == 0) {
                continue;
            }
            words.add(readGroup(groups[i], full || !words.isEmpty()));
            if (!units[i].isEmpty()) {
                words.add(units[i]);
            }
        }
        return String.join(" ", words);
    }

    /** @param full read the hundreds even when zero ("không trăm"), as inside a larger number */
    private static String readGroup(int group, boolean full) {
        int hundreds = group / 100;
        int tens = group / 10 % 10;
        int units = group % 10;
        List<String> words = new ArrayList<>();
        if (hundreds > 0 || full) {
            words.add(DIGITS[hundreds] + " trăm");
        }
        if (tens == 0) {
            if (units > 0) {
                if (hundreds > 0 || full) {
                    words.add("linh");
                }
                words.add(DIGITS[units]);
            }
        } else if (tens == 1) {
            words.add("mười");
            if (units == 5) {
                words.add("lăm");
            } else if (units > 0) {
                words.add(DIGITS[units]);
            }
        } else {
            words.add(DIGITS[tens] + " mươi");
            switch (units) {
                case 0 -> { }
                case 1 -> words.add("mốt");
                case 4 -> words.add("tư");
                case 5 -> words.add("lăm");
                default -> words.add(DIGITS[units]);
            }
        }
        return String.join(" ", words);
    }
}
