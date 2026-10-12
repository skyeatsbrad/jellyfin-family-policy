package org.familypolicy.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7001;
    private static final int PAGE_SIZE = 50;
    private static final String[] MEDIA_LABELS = {
        "Movies and shows",
        "Movies",
        "Shows",
        "Episodes",
        "All supported media"
    };
    private static final String[] MEDIA_TYPES = {
        "Movie,Series",
        "Movie",
        "Series",
        "Episode",
        "Movie,Series,Episode,Video,Audio"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<UserEntry> users = new ArrayList<>();
    private final Map<String, JSONObject> drafts = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private final List<MediaEntry> media = new ArrayList<>();

    private SecureSessionStore sessionStore;
    private JellyfinClient client;
    private JellyfinClient.Session session;
    private JellyfinClient.Session storedSession;
    private long revision;
    private int mediaStart;
    private int mediaTotal;
    private boolean suppressRatingChange;
    private boolean notificationPermissionRequested;
    private boolean updateCheckPending;
    private File pendingInstallFile;

    private EditText serverField;
    private EditText userNameField;
    private EditText passwordField;
    private TextView loginStatus;
    private TextView healthText;
    private TextView userStatusText;
    private TextView mediaStatusText;
    private TextView dirtyText;
    private TextView statusText;
    private Spinner userSpinner;
    private Spinner ratingSpinner;
    private Spinner mediaTypeSpinner;
    private EditText searchField;
    private Button previousButton;
    private Button nextButton;
    private Button restoreButton;
    private Button applyButton;
    private MediaAdapter mediaAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JellyfinTheme.applyWindow(this);
        sessionStore = new SecureSessionStore(this);
        AppUpdateManager.schedule(this);
        updateCheckPending = getIntent().getBooleanExtra(AppUpdateManager.EXTRA_CHECK_UPDATES, false);
        restoreSession();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(AppUpdateManager.EXTRA_CHECK_UPDATES, false)) {
            updateCheckPending = true;
            maybeRunPendingUpdateCheck();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstallFile != null && pendingInstallFile.isFile()
            && AppUpdateManager.requestInstallPermission(this)) {
            File install = pendingInstallFile;
            pendingInstallFile = null;
            AppUpdateManager.install(this, install);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void restoreSession() {
        String message = "";
        try {
            storedSession = sessionStore.load();
        } catch (GeneralSecurityException ex) {
            sessionStore.clearSession();
            message = "The saved session could not be decrypted and was cleared.";
        }
        showLogin(message);
        if (storedSession != null) {
            setLoginStatus("Checking the saved administrator session...");
            connect(storedSession, true);
        }
    }

    private void showLogin(String message) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(JellyfinTheme.BACKGROUND);
        applySystemBarInsets(scroll);

        LinearLayout content = verticalContainer();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = verticalColumn();
        hero.setBackground(JellyfinTheme.hero(this));
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        hero.setElevation(dp(6));

        TextView brand = text("FAMILY POLICY", 12);
        brand.setTextColor(0xE6FFFFFF);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(0.12f);
        hero.addView(brand);

        TextView title = heading("Jellyfin access,\nmade simple.", 30);
        title.setTextColor(JellyfinTheme.TEXT_PRIMARY);
        title.setLineSpacing(0, 0.95f);
        hero.addView(title, topMargin(8));
        TextView subtitle = text(
            "Set household ratings and per-title access from one secure dashboard.",
            15
        );
        subtitle.setTextColor(0xE6FFFFFF);
        subtitle.setPadding(0, dp(10), 0, 0);
        hero.addView(subtitle);
        content.addView(hero, bottomMargin(16));

        LinearLayout form = cardContainer();
        form.addView(sectionCaption("ADMINISTRATOR SIGN IN"));
        TextView formHint = text(
            "Use a Jellyfin administrator account. Your password is sent only to your server and is never stored.",
            14
        );
        formHint.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        formHint.setLineSpacing(0, 1.12f);
        form.addView(formHint, bottomMargin(10));

        form.addView(label("Jellyfin server URL"));
        serverField = input(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverField.setHint("https://jellyfin.example.com");
        serverField.setText(sessionStore.serverHint());
        form.addView(serverField, bottomMargin(6));

        form.addView(label("Administrator username"));
        userNameField = input(InputType.TYPE_CLASS_TEXT);
        userNameField.setText(sessionStore.userNameHint());
        form.addView(userNameField, bottomMargin(6));

        form.addView(label("Password"));
        passwordField = input(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                beginLogin();
                return true;
            }
            return false;
        });
        form.addView(passwordField, bottomMargin(16));

        Button signIn = primaryButton("Sign in to Jellyfin");
        signIn.setOnClickListener(view -> beginLogin());
        form.addView(signIn, bottomMargin(8));

        if (storedSession != null) {
            Button resume = secondaryButton("Retry saved session");
            resume.setOnClickListener(view -> connect(storedSession, true));
            form.addView(resume, bottomMargin(8));
        }

        Button updates = ghostButton("Check for app update");
        updates.setOnClickListener(view -> checkForUpdates(true));
        form.addView(updates);

        loginStatus = text(message, 15);
        loginStatus.setTextColor(JellyfinTheme.statusColor(message));
        loginStatus.setMinHeight(dp(28));
        loginStatus.setPadding(0, dp(12), 0, 0);
        form.addView(loginStatus);
        content.addView(form, bottomMargin(12));

        TextView security = text("Encrypted session storage. Passwords are never saved.", 13);
        security.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        security.setGravity(Gravity.CENTER);
        security.setBackground(JellyfinTheme.elevatedCard(this));
        security.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.addView(security, bottomMargin(12));

        TextView version = text("Android preview " + JellyfinClient.VERSION, 13);
        version.setTextColor(JellyfinTheme.TEXT_MUTED);
        version.setGravity(Gravity.CENTER);
        content.addView(version);
        setContentView(scroll);
        maybeRunPendingUpdateCheck();
    }

    private void beginLogin() {
        String server;
        try {
            server = JellyfinClient.normalizeBaseUrl(serverField.getText().toString());
        } catch (IllegalArgumentException ex) {
            setLoginStatus(ex.getMessage());
            return;
        }
        String username = userNameField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            setLoginStatus("Enter the administrator username and password.");
            return;
        }
        if (JellyfinClient.isCleartext(server)) {
            new AlertDialog.Builder(this)
                .setTitle("Unencrypted connection")
                .setMessage("HTTP sends the Jellyfin password and session token without transport encryption. Continue only on a trusted local network.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> performLogin(server, username, password))
                .show();
        } else {
            performLogin(server, username, password);
        }
    }

    private void performLogin(String server, String username, String password) {
        setLoginStatus("Signing in...");
        sessionStore.saveLoginHint(server, username);
        runTask(
            () -> JellyfinClient.authenticate(server, username, password, sessionStore.deviceId()),
            authenticated -> {
                passwordField.setText("");
                if (!authenticated.administrator) {
                    setLoginStatus("Family Policy requires a Jellyfin administrator account.");
                    return;
                }
                session = authenticated;
                storedSession = authenticated;
                try {
                    sessionStore.save(authenticated);
                } catch (GeneralSecurityException ex) {
                    setLoginStatus("Signed in, but Android could not securely remember the session.");
                }
                connect(authenticated, false);
            },
            error -> setLoginStatus(describeError(error))
        );
    }

    private void connect(JellyfinClient.Session target, boolean restored) {
        client = new JellyfinClient(
            target.serverUrl,
            sessionStore.deviceId(),
            target.token,
            target.userId
        );
        session = target;
        if (loginStatus != null) {
            setLoginStatus(restored ? "Loading saved session..." : "Loading Family Policy...");
        }
        runTask(
            () -> DashboardData.fetch(client),
            this::applyDashboardData,
            error -> {
                if (error instanceof JellyfinClient.HttpException
                    && ((JellyfinClient.HttpException) error).status == 401) {
                    sessionStore.clearSession();
                    storedSession = null;
                }
                showLogin(describeError(error));
            }
        );
    }

    private void applyDashboardData(DashboardData data) {
        revision = data.state.optLong("Revision", 0);
        Map<String, JSONObject> managedPolicies = new LinkedHashMap<>();
        JSONArray accounts = data.state.optJSONArray("Accounts");
        if (accounts != null) {
            for (int index = 0; index < accounts.length(); index++) {
                JSONObject account = accounts.optJSONObject(index);
                if (account != null && account.optJSONObject("Policy") != null) {
                    managedPolicies.put(
                        PolicyJson.normalizedId(account.optString("UserId")),
                        account.optJSONObject("Policy")
                    );
                }
            }
        }

        users.clear();
        drafts.clear();
        dirty.clear();
        try {
            for (int index = 0; index < data.users.length(); index++) {
                JSONObject value = data.users.optJSONObject(index);
                JSONObject nativePolicy = value == null ? null : value.optJSONObject("Policy");
                if (value == null || (nativePolicy != null && nativePolicy.optBoolean("IsAdministrator", false))) {
                    continue;
                }
                String id = value.optString("Id");
                String key = PolicyJson.normalizedId(id);
                boolean managed = managedPolicies.containsKey(key);
                JSONObject policy = managed
                    ? PolicyJson.copy(managedPolicies.get(key))
                    : PolicyJson.defaultPolicy();
                UserEntry entry = new UserEntry(id, value.optString("Name", "Unnamed user"), managed, policy);
                users.add(entry);
                drafts.put(key, PolicyJson.copy(policy));
            }
        } catch (JSONException ex) {
            showLogin("The server returned an invalid Family Policy state.");
            return;
        }
        users.sort(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        showDashboard(data.health);
    }

    private void showDashboard(JSONObject health) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(JellyfinTheme.BACKGROUND);
        applySystemBarInsets(root);

        ListView list = new ListView(this);
        list.setBackgroundColor(JellyfinTheme.BACKGROUND);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(8));

        LinearLayout controls = verticalContainer();
        controls.setPadding(dp(12), dp(12), dp(12), dp(6));

        LinearLayout hero = verticalColumn();
        hero.setBackground(JellyfinTheme.hero(this));
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        hero.setElevation(dp(6));

        LinearLayout titleRow = horizontalRow();
        LinearLayout titleColumn = verticalColumn();
        TextView title = heading("Family Policy", 27);
        title.setTextColor(JellyfinTheme.TEXT_PRIMARY);
        titleColumn.addView(title);
        TextView heroSubtitle = text("Jellyfin household access manager", 13);
        heroSubtitle.setTextColor(0xE6FFFFFF);
        titleColumn.addView(heroSubtitle);
        titleRow.addView(titleColumn, weighted());

        boolean guardHealthy = health.optBoolean("guardHealthy", false);
        String pluginVersion = health.optString("version", "unknown");
        healthText = text(guardHealthy ? "Guard healthy" : "Guard attention", 12);
        healthText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        healthText.setTextColor(guardHealthy ? JellyfinTheme.SUCCESS : JellyfinTheme.WARNING);
        healthText.setBackground(JellyfinTheme.statusPill(this, guardHealthy));
        healthText.setGravity(Gravity.CENTER);
        healthText.setPadding(dp(11), dp(7), dp(11), dp(7));
        titleRow.addView(healthText);
        hero.addView(titleRow);

        TextView server = text(client.baseUrl() + "\nPlugin " + pluginVersion, 12);
        server.setTextColor(0xCCFFFFFF);
        server.setPadding(0, dp(10), 0, dp(12));
        hero.addView(server);

        LinearLayout actions = horizontalRow();
        Button refresh = secondaryButton("Refresh");
        Button updates = secondaryButton("Updates");
        Button signOut = ghostButton("Sign out");
        actions.addView(refresh, weightedWithEndMargin(6));
        actions.addView(updates, weightedWithEndMargin(6));
        actions.addView(signOut, weighted());
        hero.addView(actions);
        controls.addView(hero, bottomMargin(12));

        LinearLayout policyCard = cardContainer();
        policyCard.addView(sectionCaption("HOUSEHOLD PROFILE"));
        policyCard.addView(label("Family member"));
        userSpinner = new Spinner(this);
        JellyfinTheme.styleSpinner(this, userSpinner);
        userSpinner.setAdapter(new DarkSpinnerAdapter<>(this, users));
        policyCard.addView(userSpinner, matchWidth());

        userStatusText = text("", 13);
        userStatusText.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        userStatusText.setPadding(0, dp(8), 0, dp(10));
        policyCard.addView(userStatusText);

        View policyDivider = new View(this);
        policyDivider.setBackground(JellyfinTheme.divider(this));
        policyCard.addView(policyDivider, dividerParams());
        policyCard.addView(sectionCaption("DEFAULT ACCESS"), topMargin(12));

        LinearLayout policyRow = horizontalRow();
        LinearLayout ratingColumn = verticalColumn();
        ratingColumn.addView(label("Rating boundary"));
        ratingSpinner = new Spinner(this);
        JellyfinTheme.styleSpinner(this, ratingSpinner);
        ratingSpinner.setAdapter(new DarkSpinnerAdapter<>(this, PolicyJson.RatingPreset.values()));
        ratingColumn.addView(ratingSpinner, matchWidth());
        policyRow.addView(ratingColumn, weightedWithEndMargin(8));
        restoreButton = dangerButton("Restore native");
        LinearLayout.LayoutParams restoreParams = fixedWidth(128);
        restoreParams.topMargin = dp(28);
        policyRow.addView(restoreButton, restoreParams);
        policyCard.addView(policyRow);
        controls.addView(policyCard, bottomMargin(12));

        LinearLayout libraryCard = cardContainer();
        libraryCard.addView(sectionCaption("LIBRARY ACCESS"));
        TextView libraryHint = text(
            "Search the library and set individual titles to inherit, allow, or deny.",
            13
        );
        libraryHint.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        libraryHint.setLineSpacing(0, 1.08f);
        libraryCard.addView(libraryHint, bottomMargin(8));

        LinearLayout searchRow = horizontalRow();
        searchField = input(InputType.TYPE_CLASS_TEXT);
        searchField.setHint("Search titles");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        Button search = secondaryButton("Search");
        searchRow.addView(searchField, weightedWithEndMargin(8));
        searchRow.addView(search, fixedWidth(86));
        libraryCard.addView(searchRow, bottomMargin(8));

        LinearLayout mediaRow = horizontalRow();
        mediaTypeSpinner = new Spinner(this);
        JellyfinTheme.styleSpinner(this, mediaTypeSpinner);
        mediaTypeSpinner.setAdapter(new DarkSpinnerAdapter<>(this, MEDIA_LABELS));
        previousButton = ghostButton("Prev");
        nextButton = ghostButton("Next");
        mediaRow.addView(mediaTypeSpinner, weightedWithEndMargin(6));
        mediaRow.addView(previousButton, fixedWidthWithEndMargin(64, 6));
        mediaRow.addView(nextButton, fixedWidth(64));
        libraryCard.addView(mediaRow);

        mediaStatusText = text("Loading library...", 13);
        mediaStatusText.setTextColor(JellyfinTheme.TEXT_MUTED);
        mediaStatusText.setPadding(0, dp(8), 0, 0);
        libraryCard.addView(mediaStatusText);
        controls.addView(libraryCard, bottomMargin(12));

        controls.addView(sectionCaption("TITLE EXCEPTIONS"));
        TextView listHint = text("Changes stay in a local draft until you apply them.", 13);
        listHint.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        listHint.setPadding(0, dp(3), 0, dp(4));
        controls.addView(listHint);
        list.addHeaderView(controls, null, false);

        mediaAdapter = new MediaAdapter(this, new MediaAdapter.StateAccess() {
            @Override
            public String stateFor(String itemId) {
                UserEntry user = selectedUser();
                JSONObject policy = user == null ? null : drafts.get(PolicyJson.normalizedId(user.id));
                return policy == null ? PolicyJson.INHERIT : PolicyJson.titleState(policy, itemId);
            }

            @Override
            public void onStateChanged(String itemId, String state) {
                changeTitleState(itemId, state);
            }
        });
        list.setAdapter(mediaAdapter);
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        LinearLayout footer = verticalColumn();
        footer.setBackground(JellyfinTheme.elevatedCard(this));
        footer.setPadding(dp(14), dp(10), dp(14), dp(12));
        footer.setElevation(dp(8));
        dirtyText = text("", 13);
        dirtyText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        footer.addView(dirtyText);
        statusText = text("", 13);
        statusText.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        statusText.setPadding(0, dp(2), 0, dp(7));
        footer.addView(statusText);
        applyButton = primaryButton("Apply all pending changes");
        footer.addView(applyButton);
        root.addView(footer, outerCardMargins());
        setContentView(root);

        refresh.setOnClickListener(view -> confirmDiscardAndReload());
        updates.setOnClickListener(view -> checkForUpdates(true));
        signOut.setOnClickListener(view -> signOut());
        search.setOnClickListener(view -> {
            mediaStart = 0;
            loadMediaPage();
        });
        searchField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mediaStart = 0;
                loadMediaPage();
                return true;
            }
            return false;
        });
        previousButton.setOnClickListener(view -> {
            mediaStart = Math.max(0, mediaStart - PAGE_SIZE);
            loadMediaPage();
        });
        nextButton.setOnClickListener(view -> {
            mediaStart += PAGE_SIZE;
            loadMediaPage();
        });
        restoreButton.setOnClickListener(view -> restoreSelectedUser());
        applyButton.setOnClickListener(view -> applyChanges());

        userSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::userSelectionChanged));
        ratingSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(this::ratingSelectionChanged));
        mediaTypeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            mediaStart = 0;
            loadMediaPage();
        }));

        updateDirtyStatus();
        if (!users.isEmpty()) {
            userSpinner.setSelection(0);
            userSelectionChanged(0);
        } else {
            userStatusText.setText("No non-administrator Jellyfin users were found.");
            userStatusText.setTextColor(JellyfinTheme.ERROR);
            restoreButton.setEnabled(false);
            ratingSpinner.setEnabled(false);
        }
        requestNotificationPermission();
        maybeRunPendingUpdateCheck();
    }

    private void userSelectionChanged(int position) {
        if (position < 0 || position >= users.size()) {
            return;
        }
        UserEntry user = users.get(position);
        userStatusText.setText(user.managed
            ? "Managed by Family Policy."
            : "Not yet managed. Applying a change enrolls this account with a PG default.");
        userStatusText.setTextColor(user.managed ? JellyfinTheme.SUCCESS : JellyfinTheme.WARNING);
        restoreButton.setEnabled(user.managed);
        JSONObject policy = drafts.get(PolicyJson.normalizedId(user.id));
        PolicyJson.RatingPreset preset = PolicyJson.detectRatingPreset(policy);
        suppressRatingChange = true;
        ratingSpinner.setSelection(preset.ordinal());
        suppressRatingChange = false;
        mediaAdapter.notifyDataSetChanged();
    }

    private void ratingSelectionChanged(int position) {
        if (suppressRatingChange || position < 0 || position >= PolicyJson.RatingPreset.values().length) {
            return;
        }
        UserEntry user = selectedUser();
        if (user == null) {
            return;
        }
        PolicyJson.RatingPreset preset = PolicyJson.RatingPreset.values()[position];
        JSONObject current = drafts.get(PolicyJson.normalizedId(user.id));
        if (preset == PolicyJson.RatingPreset.CUSTOM || preset == PolicyJson.detectRatingPreset(current)) {
            return;
        }
        try {
            drafts.put(PolicyJson.normalizedId(user.id), PolicyJson.applyRatingPreset(current, preset));
            markDirty(user.id);
            mediaAdapter.notifyDataSetChanged();
            showStatus("Rating boundary added to the local draft.");
        } catch (JSONException | IllegalArgumentException ex) {
            showStatus(ex.getMessage());
            userSelectionChanged(userSpinner.getSelectedItemPosition());
        }
    }

    private void changeTitleState(String itemId, String state) {
        UserEntry user = selectedUser();
        if (user == null) {
            return;
        }
        String key = PolicyJson.normalizedId(user.id);
        try {
            drafts.put(key, PolicyJson.setTitleState(drafts.get(key), itemId, state));
            markDirty(user.id);
            showStatus("Title access added to the local draft.");
        } catch (JSONException | IllegalArgumentException ex) {
            showStatus(ex.getMessage());
            mediaAdapter.notifyDataSetChanged();
        }
    }

    private void loadMediaPage() {
        if (client == null || mediaTypeSpinner == null) {
            return;
        }
        String types = MEDIA_TYPES[Math.max(0, mediaTypeSpinner.getSelectedItemPosition())];
        String search = searchField == null ? "" : searchField.getText().toString();
        mediaStatusText.setText("Loading library...");
        mediaStatusText.setTextColor(JellyfinTheme.TEXT_MUTED);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        int requestedStart = mediaStart;
        runTask(
            () -> client.items(types, search, requestedStart, PAGE_SIZE),
            response -> {
                media.clear();
                JSONArray items = response.optJSONArray("Items");
                if (items != null) {
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item == null) {
                            continue;
                        }
                        String type = item.optString("Type", "Media");
                        String name = item.optString("Name", "Untitled");
                        if ("Episode".equals(type) && !item.optString("SeriesName", "").isEmpty()) {
                            name = item.optString("SeriesName") + " - " + name;
                        }
                        int year = item.optInt("ProductionYear", 0);
                        String detail = type + (year > 0 ? " - " + year : "");
                        media.add(new MediaEntry(item.optString("Id"), name, detail));
                    }
                }
                mediaTotal = response.optInt("TotalRecordCount", media.size());
                int end = Math.min(mediaStart + media.size(), mediaTotal);
                mediaStatusText.setText(mediaTotal == 0
                    ? "No matching titles"
                    : (mediaStart + 1) + "-" + end + " of " + mediaTotal);
                mediaStatusText.setTextColor(JellyfinTheme.TEXT_SECONDARY);
                previousButton.setEnabled(mediaStart > 0);
                nextButton.setEnabled(mediaStart + PAGE_SIZE < mediaTotal);
                mediaAdapter.setItems(media);
            },
            error -> {
                mediaStatusText.setText("Library load failed: " + describeError(error));
                mediaStatusText.setTextColor(JellyfinTheme.ERROR);
                previousButton.setEnabled(mediaStart > 0);
                nextButton.setEnabled(false);
            }
        );
    }

    private void applyChanges() {
        if (dirty.isEmpty()) {
            showStatus("There are no pending changes.");
            return;
        }
        if (dirty.size() > 100) {
            showStatus("The server accepts at most 100 account updates at once.");
            return;
        }
        Map<String, JSONObject> changes = new LinkedHashMap<>();
        for (String key : dirty) {
            UserEntry user = userByKey(key);
            JSONObject policy = drafts.get(key);
            if (user != null && policy != null) {
                changes.put(user.id, policy);
            }
        }
        applyButton.setEnabled(false);
        showStatus("Applying " + changes.size() + " account draft" + (changes.size() == 1 ? "" : "s") + "...");
        runTask(
            () -> client.updateAccounts(revision, changes),
            response -> {
                showStatus("Changes applied.");
                reloadDashboard();
            },
            error -> {
                applyButton.setEnabled(true);
                showStatus(describeError(error));
            }
        );
    }

    private void restoreSelectedUser() {
        UserEntry user = selectedUser();
        if (user == null || !user.managed) {
            showStatus("This account is not currently managed.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Stop managing " + user.name + "?")
            .setMessage("This restores the native Jellyfin policy captured before Family Policy enrollment.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore", (dialog, which) -> runTask(
                () -> client.restoreAccount(revision, user.id),
                response -> reloadDashboard(),
                error -> showStatus(describeError(error))
            ))
            .show();
    }

    private void confirmDiscardAndReload() {
        if (dirty.isEmpty()) {
            reloadDashboard();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Discard pending changes?")
            .setMessage("Saved policies will be reloaded from Jellyfin.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Discard", (dialog, which) -> reloadDashboard())
            .show();
    }

    private void reloadDashboard() {
        showStatus("Reloading saved policies...");
        runTask(
            () -> DashboardData.fetch(client),
            this::applyDashboardData,
            error -> showStatus(describeError(error))
        );
    }

    private void signOut() {
        sessionStore.clearSession();
        storedSession = null;
        session = null;
        client = null;
        users.clear();
        drafts.clear();
        dirty.clear();
        showLogin("Signed out.");
    }

    private void markDirty(String userId) {
        dirty.add(PolicyJson.normalizedId(userId));
        updateDirtyStatus();
    }

    private void updateDirtyStatus() {
        if (dirtyText == null || applyButton == null) {
            return;
        }
        int count = dirty.size();
        dirtyText.setText(count == 0
            ? "No pending changes."
            : count + " account draft" + (count == 1 ? "" : "s") + " changed.");
        dirtyText.setTextColor(count == 0 ? JellyfinTheme.TEXT_SECONDARY : JellyfinTheme.CYAN);
        applyButton.setEnabled(count > 0);
    }

    private UserEntry selectedUser() {
        if (userSpinner == null) {
            return null;
        }
        int position = userSpinner.getSelectedItemPosition();
        return position >= 0 && position < users.size() ? users.get(position) : null;
    }

    private UserEntry userByKey(String key) {
        for (UserEntry user : users) {
            if (PolicyJson.normalizedId(user.id).equals(key)) {
                return user;
            }
        }
        return null;
    }

    private void checkForUpdates(boolean userInitiated) {
        if (userInitiated) {
            showStatus("Checking GitHub for an app update...");
        }
        runTask(
            () -> {
                return new GitHubUpdateClient().findUpdate(AppUpdateManager.installedVersionCode(this));
            },
            update -> {
                if (update == null) {
                    if (userInitiated) {
                        showStatus("This APK is up to date.");
                    }
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Update available")
                    .setMessage("Family Policy " + update.versionName
                        + " is available. The APK will be downloaded and verified before Android asks you to install it.")
                    .setNegativeButton("Later", null)
                    .setPositiveButton("Download and install", (dialog, which) -> downloadUpdate(update))
                    .show();
            },
            error -> {
                if (userInitiated) {
                    showStatus("Update check failed: " + describeError(error));
                }
            }
        );
    }

    private void downloadUpdate(GitHubUpdateClient.UpdateInfo update) {
        showStatus("Downloading and verifying " + update.assetName + "...");
        runTask(
            () -> AppUpdateManager.downloadAndVerify(this, update),
            apk -> {
                pendingInstallFile = apk;
                showStatus("Update verified. Android will ask you to approve installation.");
                if (AppUpdateManager.requestInstallPermission(this)) {
                    pendingInstallFile = null;
                    AppUpdateManager.install(this, apk);
                } else {
                    showStatus("Allow Family Policy to install unknown apps, then return to continue.");
                }
            },
            error -> showStatus("Update failed: " + describeError(error))
        );
    }

    private void maybeRunPendingUpdateCheck() {
        if (!updateCheckPending) {
            return;
        }
        updateCheckPending = false;
        checkForUpdates(true);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
            && !notificationPermissionRequested
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequested = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void showStatus(String message) {
        if (statusText != null && statusText.isAttachedToWindow()) {
            statusText.setText(message);
            statusText.setTextColor(JellyfinTheme.statusColor(message));
        } else if (loginStatus != null && loginStatus.isAttachedToWindow()) {
            setLoginStatus(message);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void setLoginStatus(String message) {
        if (loginStatus == null) {
            return;
        }
        loginStatus.setText(message == null ? "" : message);
        loginStatus.setTextColor(JellyfinTheme.statusColor(message));
    }

    private String describeError(Exception error) {
        if (error instanceof JellyfinClient.HttpException) {
            JellyfinClient.HttpException http = (JellyfinClient.HttpException) error;
            switch (http.status) {
                case 401:
                    return "The Jellyfin session is no longer valid. Sign in again.";
                case 403:
                    return "Jellyfin administrator access is required.";
                case 404:
                    return "The Family Policy plugin endpoint was not found on this server.";
                case 409:
                    return "Another administrator changed the policy. Pending drafts were kept; refresh before retrying.";
                case 503:
                    return http.serverMessage();
                default:
                    return http.serverMessage();
            }
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private <T> void runTask(Task<T> task, Consumer<T> success, Consumer<Exception> failure) {
        executor.execute(() -> {
            try {
                T result = task.run();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        success.accept(result);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        failure.accept(error);
                    }
                });
            }
        });
    }

    private LinearLayout verticalContainer() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackgroundColor(JellyfinTheme.BACKGROUND);
        return layout;
    }

    private LinearLayout cardContainer() {
        LinearLayout layout = verticalColumn();
        layout.setBackground(JellyfinTheme.card(this));
        layout.setPadding(dp(16), dp(15), dp(16), dp(16));
        layout.setElevation(dp(2));
        return layout;
    }

    private LinearLayout verticalColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontalRow() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView heading(String value, int size) {
        TextView text = text(value, size);
        JellyfinTheme.styleHeading(text);
        return text;
    }

    private TextView sectionCaption(String value) {
        TextView text = text(value, 12);
        JellyfinTheme.styleSectionCaption(text);
        return text;
    }

    private TextView label(String value) {
        TextView text = text(value, 13);
        text.setTextColor(JellyfinTheme.TEXT_SECONDARY);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setPadding(0, dp(8), 0, dp(5));
        return text;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(JellyfinTheme.TEXT_PRIMARY);
        text.setFontFeatureSettings("kern");
        return text;
    }

    private EditText input(int inputType) {
        EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setSingleLine(true);
        JellyfinTheme.styleInput(this, input);
        input.setLayoutParams(matchWidth());
        return input;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        JellyfinTheme.stylePrimaryButton(this, button);
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        JellyfinTheme.styleSecondaryButton(this, button);
        return button;
    }

    private Button ghostButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        JellyfinTheme.styleGhostButton(this, button);
        return button;
    }

    private Button dangerButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        JellyfinTheme.styleDangerButton(this, button);
        return button;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightedWithEndMargin(int margin) {
        LinearLayout.LayoutParams params = weighted();
        params.rightMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams fixedWidth(int width) {
        return new LinearLayout.LayoutParams(dp(width), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fixedWidthWithEndMargin(int width, int margin) {
        LinearLayout.LayoutParams params = fixedWidth(width);
        params.rightMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams bottomMargin(int margin) {
        LinearLayout.LayoutParams params = matchWidth();
        params.bottomMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = matchWidth();
        params.topMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams dividerParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        );
    }

    private LinearLayout.LayoutParams outerCardMargins() {
        LinearLayout.LayoutParams params = matchWidth();
        params.setMargins(dp(10), dp(4), dp(10), dp(10));
        return params;
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarInsets(View root) {
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                left + insets.getSystemWindowInsetLeft(),
                top + insets.getSystemWindowInsetTop(),
                right + insets.getSystemWindowInsetRight(),
                bottom + insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
    }

    private int dp(int value) {
        return JellyfinTheme.dp(this, value);
    }

    private interface Task<T> {
        T run() throws Exception;
    }

    private static final class DashboardData {
        final JSONObject health;
        final JSONArray users;
        final JSONObject state;

        DashboardData(JSONObject health, JSONArray users, JSONObject state) {
            this.health = health;
            this.users = users;
            this.state = state;
        }

        static DashboardData fetch(JellyfinClient client) throws Exception {
            return new DashboardData(client.health(), client.users(), client.state());
        }
    }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Consumer<Integer> selected;

        SimpleItemSelectedListener(Consumer<Integer> selected) {
            this.selected = selected;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            selected.accept(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
