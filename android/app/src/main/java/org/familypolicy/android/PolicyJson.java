package org.familypolicy.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PolicyJson {
    public static final String INHERIT = "inherit";
    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    private static final int MAX_RULES = 256;
    private static final int MAX_TITLE_VALUES = 128;

    private PolicyJson() {
    }

    public enum RatingPreset {
        CUSTOM("Custom policy", null, false),
        PG("PG / TV-PG or lower", 10, false),
        PG13("PG-13 or lower", 13, false),
        TV14("TV-14 or lower", 14, false),
        R("R / TV-MA or lower", 17, false),
        UNRESTRICTED("No default rating ceiling", null, true);

        private final String label;
        private final Integer ceiling;
        private final boolean unrestricted;

        RatingPreset(String label, Integer ceiling, boolean unrestricted) {
            this.label = label;
            this.ceiling = ceiling;
            this.unrestricted = unrestricted;
        }

        public Integer ceiling() {
            return ceiling;
        }

        public boolean unrestricted() {
            return unrestricted;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static JSONObject defaultPolicy() throws JSONException {
        JSONObject when = new JSONObject()
            .put("Field", "RatingAtMost")
            .put("Number", 10);
        JSONObject rule = new JSONObject()
            .put("Id", "pg-default")
            .put("Dimension", "Content")
            .put("Effect", "Allow")
            .put("Priority", 0)
            .put("When", when);
        return new JSONObject()
            .put("SchemaVersion", 1)
            .put("TimeZone", "UTC")
            .put("DefaultContentAllowed", false)
            .put("DefaultTimeAllowed", true)
            .put("Rules", new JSONArray().put(rule));
    }

    public static JSONObject copy(JSONObject source) throws JSONException {
        return new JSONObject(source.toString());
    }

    public static String normalizedId(String value) {
        return value == null ? "" : value.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String titleState(JSONObject policy, String itemId) {
        String normalizedItem = normalizedId(itemId);
        int maxPriority = Integer.MIN_VALUE;
        boolean matched = false;
        boolean denyAtMax = false;
        JSONArray rules = policy.optJSONArray("Rules");
        if (rules == null) {
            return INHERIT;
        }
        for (int index = 0; index < rules.length(); index++) {
            JSONObject rule = rules.optJSONObject(index);
            if (!isSimpleTitleRule(rule) || !isActive(rule)) {
                continue;
            }
            JSONArray values = rule.optJSONObject("When").optJSONArray("Values");
            if (!containsId(values, normalizedItem)) {
                continue;
            }
            int priority = rule.optInt("Priority", 0);
            boolean deny = "Deny".equalsIgnoreCase(rule.optString("Effect", "Deny"));
            if (!matched || priority > maxPriority) {
                matched = true;
                maxPriority = priority;
                denyAtMax = deny;
            } else if (priority == maxPriority && deny) {
                denyAtMax = true;
            }
        }
        if (!matched) {
            return INHERIT;
        }
        return denyAtMax ? DENY : ALLOW;
    }

    public static JSONObject setTitleState(JSONObject policy, String itemId, String state) throws JSONException {
        if (!INHERIT.equals(state) && !ALLOW.equals(state) && !DENY.equals(state)) {
            throw new IllegalArgumentException("Unknown title state.");
        }
        String normalizedItem = normalizedId(itemId);
        JSONObject updated = copy(policy);
        JSONArray originalRules = updated.optJSONArray("Rules");
        if (originalRules == null) {
            originalRules = new JSONArray();
        }
        JSONArray retained = new JSONArray();
        Set<String> allow = new HashSet<>();
        Set<String> deny = new HashSet<>();

        for (int index = 0; index < originalRules.length(); index++) {
            JSONObject rule = originalRules.optJSONObject(index);
            String matrixEffect = matrixEffect(rule);
            if (matrixEffect != null) {
                JSONArray values = rule.optJSONObject("When").optJSONArray("Values");
                collectExcept(values, normalizedItem, ALLOW.equals(matrixEffect) ? allow : deny);
                continue;
            }
            if (isSimpleTitleRule(rule)) {
                JSONObject ruleCopy = copy(rule);
                JSONObject when = ruleCopy.getJSONObject("When");
                JSONArray values = when.optJSONArray("Values");
                JSONArray filtered = filterOut(values, normalizedItem);
                if (filtered.length() > 0) {
                    when.put("Values", filtered);
                    retained.put(ruleCopy);
                }
            } else if (rule != null) {
                retained.put(copy(rule));
            }
        }

        deny.forEach(allow::remove);
        allow.remove(normalizedItem);
        deny.remove(normalizedItem);
        if (ALLOW.equals(state)) {
            allow.add(normalizedItem);
        } else if (DENY.equals(state)) {
            deny.add(normalizedItem);
        }
        appendTitleRules(retained, "Allow", allow);
        appendTitleRules(retained, "Deny", deny);
        if (retained.length() > MAX_RULES) {
            throw new IllegalArgumentException("This policy would exceed the 256-rule limit.");
        }
        updated.put("Rules", retained);
        return updated;
    }

    public static RatingPreset detectRatingPreset(JSONObject policy) {
        if (policy.optBoolean("DefaultContentAllowed", false)) {
            return RatingPreset.UNRESTRICTED;
        }
        Set<Integer> ceilings = new HashSet<>();
        JSONArray rules = policy.optJSONArray("Rules");
        if (rules == null) {
            return RatingPreset.CUSTOM;
        }
        for (int index = 0; index < rules.length(); index++) {
            JSONObject rule = rules.optJSONObject(index);
            if (isBaseRatingRule(rule) && isActive(rule)) {
                ceilings.add(rule.optJSONObject("When").optInt("Number"));
            }
        }
        if (ceilings.size() != 1) {
            return RatingPreset.CUSTOM;
        }
        int ceiling = ceilings.iterator().next();
        for (RatingPreset preset : RatingPreset.values()) {
            if (preset.ceiling != null && preset.ceiling == ceiling) {
                return preset;
            }
        }
        return RatingPreset.CUSTOM;
    }

    public static JSONObject applyRatingPreset(JSONObject policy, RatingPreset preset) throws JSONException {
        if (preset == RatingPreset.CUSTOM) {
            return copy(policy);
        }
        JSONObject updated = copy(policy);
        JSONArray rules = updated.optJSONArray("Rules");
        JSONArray retained = new JSONArray();
        if (rules != null) {
            for (int index = 0; index < rules.length(); index++) {
                JSONObject rule = rules.optJSONObject(index);
                if (isBaseRatingRule(rule)) {
                    continue;
                }
                if (rule != null) {
                    retained.put(copy(rule));
                }
            }
        }
        updated.put("DefaultContentAllowed", preset.unrestricted);
        if (!preset.unrestricted) {
            JSONObject when = new JSONObject()
                .put("Field", "RatingAtMost")
                .put("Number", preset.ceiling);
            retained.put(new JSONObject()
                .put("Id", "android-rating-ceiling")
                .put("Dimension", "Content")
                .put("Effect", "Allow")
                .put("Priority", 0)
                .put("When", when));
        }
        if (retained.length() > MAX_RULES) {
            throw new IllegalArgumentException("This policy would exceed the 256-rule limit.");
        }
        updated.put("Rules", retained);
        if (!updated.has("SchemaVersion")) {
            updated.put("SchemaVersion", 1);
        }
        if (!updated.has("TimeZone")) {
            updated.put("TimeZone", "UTC");
        }
        if (!updated.has("DefaultTimeAllowed")) {
            updated.put("DefaultTimeAllowed", true);
        }
        return updated;
    }

    private static boolean isBaseRatingRule(JSONObject rule) {
        if (rule == null || !isContentDimension(rule) || !"Allow".equalsIgnoreCase(rule.optString("Effect", "Deny"))
            || rule.optInt("Priority", 0) != 0) {
            return false;
        }
        JSONObject when = rule.optJSONObject("When");
        return when != null && "RatingAtMost".equals(when.optString("Field")) && when.has("Number");
    }

    private static boolean isSimpleTitleRule(JSONObject rule) {
        if (rule == null || !isContentDimension(rule)) {
            return false;
        }
        JSONObject when = rule.optJSONObject("When");
        return when != null && "Title".equals(when.optString("Field")) && when.optJSONArray("Values") != null;
    }

    private static boolean isContentDimension(JSONObject rule) {
        Object dimension = rule.opt("Dimension");
        if (dimension == null || dimension == JSONObject.NULL) {
            return true;
        }
        if (dimension instanceof Number) {
            return ((Number) dimension).intValue() == 0;
        }
        return "Content".equalsIgnoreCase(String.valueOf(dimension));
    }

    private static boolean isActive(JSONObject rule) {
        if (rule.has("Enabled") && !rule.optBoolean("Enabled", true)) {
            return false;
        }
        String expiresAt = rule.optString("ExpiresAt", "");
        if (expiresAt.isEmpty()) {
            return true;
        }
        try {
            return Instant.parse(expiresAt).isAfter(Instant.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static String matrixEffect(JSONObject rule) {
        if (!isSimpleTitleRule(rule)) {
            return null;
        }
        String id = rule.optString("Id", "");
        if (id.startsWith("matrix-title-allow-")) {
            return ALLOW;
        }
        if (id.startsWith("matrix-title-deny-")) {
            return DENY;
        }
        return null;
    }

    private static boolean containsId(JSONArray values, String normalizedItem) {
        if (values == null) {
            return false;
        }
        for (int index = 0; index < values.length(); index++) {
            if (normalizedItem.equals(normalizedId(values.optString(index)))) {
                return true;
            }
        }
        return false;
    }

    private static void collectExcept(JSONArray values, String excluded, Set<String> output) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            String value = normalizedId(values.optString(index));
            if (!value.isEmpty() && !value.equals(excluded)) {
                output.add(value);
            }
        }
    }

    private static JSONArray filterOut(JSONArray values, String excluded) {
        JSONArray filtered = new JSONArray();
        if (values == null) {
            return filtered;
        }
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index);
            if (!normalizedId(value).equals(excluded)) {
                filtered.put(value);
            }
        }
        return filtered;
    }

    private static void appendTitleRules(JSONArray rules, String effect, Set<String> values) throws JSONException {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        for (int start = 0, chunk = 1; start < sorted.size(); start += MAX_TITLE_VALUES, chunk++) {
            JSONArray packed = new JSONArray();
            for (int index = start; index < Math.min(start + MAX_TITLE_VALUES, sorted.size()); index++) {
                packed.put(sorted.get(index));
            }
            JSONObject when = new JSONObject()
                .put("Field", "Title")
                .put("Values", packed);
            rules.put(new JSONObject()
                .put("Id", "matrix-title-" + effect.toLowerCase(Locale.ROOT) + "-" + String.format(Locale.ROOT, "%03d", chunk))
                .put("Dimension", "Content")
                .put("Effect", effect)
                .put("Priority", 100)
                .put("When", when));
        }
    }
}
