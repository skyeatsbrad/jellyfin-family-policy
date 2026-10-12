package org.familypolicy.android;

import org.json.JSONObject;

final class UserEntry {
    final String id;
    final String name;
    final boolean managed;
    final JSONObject policy;

    UserEntry(String id, String name, boolean managed, JSONObject policy) {
        this.id = id;
        this.name = name;
        this.managed = managed;
        this.policy = policy;
    }

    @Override
    public String toString() {
        return name + (managed ? "" : " (not managed)");
    }
}
