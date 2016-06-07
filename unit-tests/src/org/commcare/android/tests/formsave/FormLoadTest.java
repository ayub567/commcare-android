package org.commcare.android.tests.formsave;

import android.content.Intent;
import android.os.Environment;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.ActivityLaunchUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.session.CommCareSession;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormLoadTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/receprofile.ccpr",
                "test", "123");

        TestUtils.processResourceTransactionIntoAppDb("/commcare-apps/rec/rectest_restore.xml");
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void loadSlowFormTest() {
        CommCareSession session = CommCareApplication._().getCurrentSession();
        session.setCommand("m1");
        session.setDatum("case_id", "d584241b-420c-4e12-a370-7a9680857731");
        session.setCommand("m3");
        session.setDatum("case_id_new_imci_visit_0", "case_id_new_imci_visit_0");
        session.setDatum("case_id_case_visit", "484ed1ea-b0c6-441e-8cc2-88f66fc22313");
        ShadowActivity shadowActivity =
                ActivityLaunchUtils.buildHomeActivityForFormEntryLaunch("m3-f3");

        Intent formEntryIntent = shadowActivity.getNextStartedActivity();

        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));
        Robolectric.buildActivity(FormEntryActivity.class).withIntent(formEntryIntent)
                .create().start().resume().get();

        try {
            writeInstanceToFile(FormEntryActivity.mFormController.getInstance());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private static void writeInstanceToFile(FormInstance instance) throws IOException {
        XFormSerializingVisitor serializer = new XFormSerializingVisitor(false);
        ByteArrayPayload payload = (ByteArrayPayload)serializer.createSerializedPayload(instance);
        FileOutputStream output = new FileOutputStream("out.xml");

        try {
            InputStream is = payload.getPayloadStream();
            StreamsUtil.writeFromInputToOutput(is, output);
        } finally {
            output.close();
        }
    }
}
