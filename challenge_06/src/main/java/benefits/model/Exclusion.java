package benefits.model;

import java.util.List;
import java.util.Locale;

/** A policy exclusion: an expense whose diagnosis contains any keyword (case-insensitive) is not covered. */
public record Exclusion(String code, String description, List<String> keywords) {

    public boolean matches(String diagnosis) {
        if (diagnosis == null) {
            return false;
        }
        String text = diagnosis.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(k -> text.contains(k.toLowerCase(Locale.ROOT)));
    }
}
