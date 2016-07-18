package org.commcare.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.R;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.RemoteQuerySessionManager;
import org.commcare.suite.model.DisplayData;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.tasks.SimpleHttpTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.media.MediaLayout;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.TreeElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * Collects 'query datum' in the current session. Prompts user for query
 * params, makes query to server and stores xml 'fixture' response into current
 * session. Allows for 'case search and claim' workflow when used inside a
 * 'sync-request' entry in conjuction with entity select datum and sync
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@ManagedUi(R.layout.http_request_layout)
public class QueryRequestActivity
        extends SaveSessionCommCareActivity<QueryRequestActivity>
        implements HttpResponseProcessor {
    private static final String TAG = QueryRequestActivity.class.getSimpleName();

    private static final String ANSWERED_USER_PROMPTS_KEY = "answered_user_prompts";
    private static final String IN_ERROR_STATE_KEY = "in-error-state-key";
    private static final String ERROR_MESSAGE_KEY = "error-message-key";

    @UiElement(value = R.id.request_button, locale = "query.button")
    private Button queryButton;

    @UiElement(value = R.id.error_message)
    private TextView errorTextView;

    private boolean inErrorState;
    private String errorMessage;
    private RemoteQuerySessionManager remoteQuerySessionManager;
    private final Hashtable<String, EditText> promptsBoxes = new Hashtable<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidSessionWrapper sessionWrapper = CommCareApplication._().getCurrentSessionWrapper();
        remoteQuerySessionManager =
                RemoteQuerySessionManager.buildQuerySessionManager(sessionWrapper.getSession(),
                        sessionWrapper.getEvaluationContext());

        if (remoteQuerySessionManager == null) {
            Log.e(TAG, "Tried to launch remote query activity at wrong time in session.");
            setResult(RESULT_CANCELED);
            finish();
        } else {
            loadStateFromSavedInstance(savedInstanceState);

            setupUI();
        }
    }

    private void setupUI() {
        buildPromptUI();

        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewUtil.hideVirtualKeyboard(QueryRequestActivity.this);
                answerPrompts();
                makeQueryRequest();
            }
        });

        if (inErrorState) {
            enterErrorState();
        }
    }

    private void buildPromptUI() {
        LinearLayout promptsLayout = (LinearLayout)findViewById(R.id.query_prompts);
        Hashtable<String, DisplayUnit> userInputDisplays =
                remoteQuerySessionManager.getNeededUserInputDisplays();
        int promptCount = 1;
        for (Map.Entry<String, DisplayUnit> displayEntry : userInputDisplays.entrySet()) {
            boolean isLastPrompt = promptCount++ == userInputDisplays.size();
            buildPromptEntry(promptsLayout, displayEntry.getKey(),
                    displayEntry.getValue(), isLastPrompt);
        }
    }

    private void buildPromptEntry(LinearLayout promptsLayout, String promptId,
                                  DisplayUnit displayUnit, boolean isLastPrompt) {
        Hashtable<String, String> userAnswers =
                remoteQuerySessionManager.getUserAnswers();
        promptsLayout.addView(createPromptMedia(displayUnit));

        EditText promptEditText = new EditText(this);
        if (userAnswers.containsKey(promptId)) {
            promptEditText.setText(userAnswers.get(promptId));
        }
        promptEditText.setBackgroundResource(R.drawable.login_edit_text);
        // needed to allow 'done' and 'next' keyboard action
        promptEditText.setSingleLine();

        if (isLastPrompt) {
            promptEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            // replace 'done' on keyboard with 'next'
            promptEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
        promptsLayout.addView(promptEditText);
        promptsBoxes.put(promptId, promptEditText);
    }

    private MediaLayout createPromptMedia(DisplayUnit display) {
        DisplayData displayData = display.evaluate();
        String promptText =
                Localizer.processArguments(displayData.getName(), new String[]{""}).trim();
        TextView text = new TextView(getApplicationContext());
        text.setText(promptText);
        text.setPadding(0, 0, 0, 7);
        text.setTextColor(Color.BLACK);

        MediaLayout helpLayout = new MediaLayout(this);
        helpLayout.setAVT(text, displayData.getAudioURI(), displayData.getImageURI(), true);
        int padding = (int)getResources().getDimension(R.dimen.help_text_padding);
        helpLayout.setPadding(padding, padding, padding, padding);

        return helpLayout;
    }

    private void answerPrompts() {
        remoteQuerySessionManager.clearAnswers();

        for (Map.Entry<String, EditText> promptEntry : promptsBoxes.entrySet()) {
            String promptText = promptEntry.getValue().getText().toString();
            if (!"".equals(promptText)) {
                remoteQuerySessionManager.answerUserPrompt(promptEntry.getKey(), promptText);
            }
        }
    }

    private void makeQueryRequest() {
        clearErrorState();
        URL url = remoteQuerySessionManager.getBaseUrl();

        SimpleHttpTask httpTask;
        try {
            httpTask = new SimpleHttpTask(this, url,
                    new HashMap<>(remoteQuerySessionManager.getRawQueryParams()),
                    false);
        } catch (ModernHttpRequester.PlainTextPasswordException e) {
            enterErrorState(Localization.get("post.not.using.https", url.toString()));
            return;
        }
        httpTask.connect((CommCareTaskConnector)this);
        httpTask.executeParallel();
    }

    private void clearErrorState() {
        errorMessage = "";
        inErrorState = false;
    }

    private void enterErrorState(String message) {
        errorMessage = message;
        enterErrorState();
    }

    private void enterErrorState() {
        inErrorState = true;
        Log.e(TAG, errorMessage);
        errorTextView.setText(errorMessage);
        errorTextView.setVisibility(View.VISIBLE);
    }

    private void loadStateFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            errorMessage = savedInstanceState.getString(ERROR_MESSAGE_KEY);
            inErrorState = savedInstanceState.getBoolean(IN_ERROR_STATE_KEY);
            Hashtable<String, String> answeredPrompts =
                    (Hashtable<String, String>)savedInstanceState.getSerializable(ANSWERED_USER_PROMPTS_KEY);
            if (answeredPrompts != null) {
                for (Map.Entry<String, String> entry : answeredPrompts.entrySet()) {
                    remoteQuerySessionManager.answerUserPrompt(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        answerPrompts();

        savedInstanceState.putSerializable(ANSWERED_USER_PROMPTS_KEY,
                remoteQuerySessionManager.getUserAnswers());
        savedInstanceState.putString(ERROR_MESSAGE_KEY, errorMessage);
        savedInstanceState.putBoolean(IN_ERROR_STATE_KEY, inErrorState);
    }

    @Override
    public void processSuccess(int responseCode, InputStream responseData) {
        Pair<ExternalDataInstance, String> instanceOrError =
                buildExternalDataInstance(responseData,
                        remoteQuerySessionManager.getStorageInstanceName());
        if (instanceOrError.first == null) {
            enterErrorState(Localization.get("query.response.format.error",
                    instanceOrError.second));
        } else if (isResponseEmpty(instanceOrError.first)) {
            Toast.makeText(this, Localization.get("query.response.empty"), Toast.LENGTH_SHORT).show();
        } else {
            CommCareApplication._().getCurrentSession().setQueryDatum(instanceOrError.first);
            setResult(RESULT_OK);
            finish();
        }
    }

    /**
     * @return Data instance built from xml stream or the error message raised during parsing
     */
    public static Pair<ExternalDataInstance, String> buildExternalDataInstance(InputStream instanceStream,
                                                                               String instanceId) {
        TreeElement root;
        try {
            KXmlParser baseParser = ElementParser.instantiateParser(instanceStream);
            root = new TreeElementParser(baseParser, 0, instanceId).parse();
        } catch (InvalidStructureException | IOException
                | XmlPullParserException | UnfullfilledRequirementsException e) {
            return new Pair<>(null, e.getMessage());
        }
        return new Pair<>(ExternalDataInstance.buildFromRemote(instanceId, root), "");
    }

    private boolean isResponseEmpty(ExternalDataInstance instance) {
        return !instance.getRoot().hasChildren();
    }

    @Override
    public void processRedirection(int responseCode) {
        enterErrorState(Localization.get("post.redirection.error", responseCode + ""));
    }

    @Override
    public void processClientError(int responseCode) {
        enterErrorState(Localization.get("post.client.error", responseCode + ""));
    }

    @Override
    public void processServerError(int responseCode) {
        enterErrorState(Localization.get("post.server.error", responseCode + ""));
    }

    @Override
    public void processOther(int responseCode) {
        enterErrorState(Localization.get("post.unknown.response", responseCode + ""));
    }

    @Override
    public void handleIOException(IOException exception) {
        enterErrorState(Localization.get("post.io.error", exception.getMessage()));
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case SimpleHttpTask.SIMPLE_HTTP_TASK_ID:
                title = Localization.get("query.dialog.title");
                message = Localization.get("query.dialog.body");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
}
