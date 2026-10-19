package benefits.io;

import benefits.json.Json;
import benefits.json.JsonObject;
import benefits.model.Benefit;
import benefits.model.Copay;
import benefits.model.Exclusion;
import benefits.model.Money;
import benefits.model.Policy;
import benefits.model.SubBenefit;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads and validates a policy JSON. Invalid input throws {@link IllegalArgumentException} naming the field. */
public final class PolicyReader {

    private PolicyReader() {
    }

    public static Policy read(Path path) throws IOException {
        return fromJson(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
    }

    public static Policy fromJson(Object json) {
        JsonObject p = JsonObject.of(json, "policy");
        LocalDate effective = p.requiredDate("effective_date");
        LocalDate expiry = p.requiredDate("expiry_date");
        if (expiry.isBefore(effective)) {
            throw p.error("expiry_date", "must not be before effective_date");
        }
        List<Exclusion> exclusions = p.objectList("exclusions", false).stream().map(PolicyReader::exclusion).toList();

        List<JsonObject> benefitNodes = p.objectList("benefits", true);
        if (benefitNodes.isEmpty()) {
            throw p.error("benefits", "must contain at least one benefit");
        }
        List<Benefit> benefits = new ArrayList<>();
        Set<String> types = new HashSet<>();
        for (JsonObject node : benefitNodes) {
            Benefit b = benefit(node);
            if (!types.add(b.type())) {
                throw node.error("benefit_type", "duplicate benefit type " + b.type());
            }
            benefits.add(b);
        }
        return new Policy(p.requiredString("policy_id"), p.optionalString("policy_holder", ""),
                p.optionalString("currency", "THB").toUpperCase(Locale.ROOT), effective, expiry,
                positive(p, "annual_limit"), exclusions, List.copyOf(benefits));
    }

    private static Benefit benefit(JsonObject b) {
        List<JsonObject> subNodes = b.objectList("sub_benefits", true);
        if (subNodes.isEmpty()) {
            throw b.error("sub_benefits", "must contain at least one sub-benefit");
        }
        List<SubBenefit> subs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonObject node : subNodes) {
            SubBenefit s = subBenefit(node);
            if (!names.add(s.name().toLowerCase(Locale.ROOT))) {
                throw node.error("name", "duplicate sub-benefit " + s.name());
            }
            subs.add(s);
        }
        BigDecimal deductible = b.has("deductible") ? nonNegative(b, "deductible") : Money.ZERO;
        Copay copay = copay(b.optionalObject("copay"));
        Integer waiting = nonNegativeInt(b, "waiting_period_days");
        return new Benefit(b.requiredString("benefit_type").toUpperCase(Locale.ROOT), positive(b, "annual_limit"),
                deductible, copay != null ? copay : Copay.NONE, waiting != null ? waiting : 0,
                positiveInt(b, "max_visits"), List.copyOf(subs));
    }

    private static SubBenefit subBenefit(JsonObject s) {
        return new SubBenefit(s.requiredString("name"),
                s.has("per_visit_limit") ? positive(s, "per_visit_limit") : null,
                s.has("annual_limit") ? positive(s, "annual_limit") : null,
                positiveInt(s, "max_visits"), copay(s.optionalObject("copay")), nonNegativeInt(s, "waiting_period_days"));
    }

    /** Null when absent (sub-benefit then inherits the benefit copay). */
    private static Copay copay(JsonObject c) {
        if (c == null) {
            return null;
        }
        String type = c.requiredString("type").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "NONE" -> Copay.NONE;
            case "PERCENTAGE" -> {
                BigDecimal v = c.requiredDecimal("value");
                if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw c.error("value", "must be between 0 and 100 for a PERCENTAGE copay");
                }
                yield Copay.percentage(v);
            }
            case "FIXED" -> Copay.fixed(nonNegative(c, "value"));
            default -> throw c.error("type", "must be PERCENTAGE, FIXED or NONE");
        };
    }

    private static Exclusion exclusion(JsonObject e) {
        List<String> keywords = e.stringList("keywords");
        if (keywords.isEmpty()) {
            throw e.error("keywords", "must contain at least one keyword");
        }
        return new Exclusion(e.requiredString("code"), e.optionalString("description", ""), keywords);
    }

    private static BigDecimal positive(JsonObject o, String key) {
        BigDecimal v = o.requiredDecimal(key);
        if (v.signum() <= 0) {
            throw o.error(key, "must be greater than 0");
        }
        return Money.scale(v);
    }

    private static BigDecimal nonNegative(JsonObject o, String key) {
        BigDecimal v = o.requiredDecimal(key);
        if (v.signum() < 0) {
            throw o.error(key, "must not be negative");
        }
        return Money.scale(v);
    }

    private static Integer positiveInt(JsonObject o, String key) {
        Integer v = o.optionalInt(key);
        if (v != null && v <= 0) {
            throw o.error(key, "must be greater than 0");
        }
        return v;
    }

    private static Integer nonNegativeInt(JsonObject o, String key) {
        Integer v = o.optionalInt(key);
        if (v != null && v < 0) {
            throw o.error(key, "must not be negative");
        }
        return v;
    }
}
