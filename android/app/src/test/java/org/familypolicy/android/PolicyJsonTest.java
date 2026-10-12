package org.familypolicy.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class PolicyJsonTest {
    @Test
    public void defaultPolicyUsesPgBoundary() throws Exception {
        JSONObject policy = PolicyJson.defaultPolicy();
        assertEquals(PolicyJson.RatingPreset.PG, PolicyJson.detectRatingPreset(policy));
        assertFalse(policy.getBoolean("DefaultContentAllowed"));
    }

    @Test
    public void denyWinsAtEqualTitlePriority() throws Exception {
        JSONObject policy = PolicyJson.defaultPolicy();
        JSONArray rules = policy.getJSONArray("Rules");
        rules.put(titleRule("allow-custom", "Allow", 100, "abc"));
        rules.put(titleRule("deny-custom", "Deny", 100, "abc"));
        assertEquals(PolicyJson.DENY, PolicyJson.titleState(policy, "a-b-c"));
    }

    @Test
    public void titleStateRebuildPreservesOtherRulesAndPacksValues() throws Exception {
        JSONObject policy = PolicyJson.defaultPolicy();
        JSONArray values = new JSONArray();
        for (int index = 0; index < 129; index++) {
            values.put(String.format("%032x", index + 1));
        }
        policy.getJSONArray("Rules").put(new JSONObject()
            .put("Id", "matrix-title-allow-001")
            .put("Dimension", "Content")
            .put("Effect", "Allow")
            .put("Priority", 100)
            .put("When", new JSONObject().put("Field", "Title").put("Values", values)));

        JSONObject updated = PolicyJson.setTitleState(
            policy,
            "ffffffffffffffffffffffffffffffff",
            PolicyJson.ALLOW
        );
        int matrixRules = 0;
        int matrixValues = 0;
        JSONArray rules = updated.getJSONArray("Rules");
        for (int index = 0; index < rules.length(); index++) {
            JSONObject rule = rules.getJSONObject(index);
            if (rule.getString("Id").startsWith("matrix-title-allow-")) {
                matrixRules++;
                int count = rule.getJSONObject("When").getJSONArray("Values").length();
                assertTrue(count <= 128);
                matrixValues += count;
            }
        }
        assertEquals(2, matrixRules);
        assertEquals(130, matrixValues);
        assertEquals(PolicyJson.ALLOW, PolicyJson.titleState(updated, "ffffffff-ffff-ffff-ffff-ffffffffffff"));
        assertEquals(PolicyJson.RatingPreset.PG, PolicyJson.detectRatingPreset(updated));
    }

    @Test
    public void ratingPresetKeepsTitleOverrides() throws Exception {
        JSONObject policy = PolicyJson.setTitleState(
            PolicyJson.defaultPolicy(),
            "abc",
            PolicyJson.DENY
        );
        JSONObject updated = PolicyJson.applyRatingPreset(policy, PolicyJson.RatingPreset.TV14);
        assertEquals(PolicyJson.RatingPreset.TV14, PolicyJson.detectRatingPreset(updated));
        assertEquals(PolicyJson.DENY, PolicyJson.titleState(updated, "abc"));
    }

    @Test
    public void unrestrictedPresetRemovesOnlyBaseRatingRule() throws Exception {
        JSONObject policy = PolicyJson.defaultPolicy();
        policy.getJSONArray("Rules").put(new JSONObject()
            .put("Id", "scheduled-deny")
            .put("Dimension", "Time")
            .put("Effect", "Deny")
            .put("Priority", 5)
            .put("When", new JSONObject().put("Field", "Always")));
        JSONObject updated = PolicyJson.applyRatingPreset(policy, PolicyJson.RatingPreset.UNRESTRICTED);
        assertEquals(PolicyJson.RatingPreset.UNRESTRICTED, PolicyJson.detectRatingPreset(updated));
        assertTrue(updated.getBoolean("DefaultContentAllowed"));
        assertEquals(1, updated.getJSONArray("Rules").length());
        assertEquals("scheduled-deny", updated.getJSONArray("Rules").getJSONObject(0).getString("Id"));
    }

    private static JSONObject titleRule(String id, String effect, int priority, String itemId) throws Exception {
        return new JSONObject()
            .put("Id", id)
            .put("Dimension", "Content")
            .put("Effect", effect)
            .put("Priority", priority)
            .put("When", new JSONObject()
                .put("Field", "Title")
                .put("Values", new JSONArray().put(itemId)));
    }
}
