package org.commcare.android.mocks;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;

import org.commcare.core.network.bitcache.BitCacheFactory;
import org.commcare.network.AndroidModernHttpRequester;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequesterMock extends AndroidModernHttpRequester {
    private static final List<Integer> responseCodeStack = new ArrayList<>();
    private static final List<String> expectedUrlStack = new ArrayList<>();
    private static final List<String> requestPayloadStack = new ArrayList<>();

    public ModernHttpRequesterMock(BitCacheFactory.CacheDirSetup cacheDirSetup,
                                   URL url, HashMap<String, String> params,
                                   User user, String domain,
                                   boolean isAuthenticatedRequest,
                                   boolean isPostRequest) {
        super(cacheDirSetup, url, params, user, domain,
                isAuthenticatedRequest, isPostRequest);
    }

    /**
     * Set the response code for the next N requests
     */
    public static void setResponseCodes(Integer[] responseCodes) {
        responseCodeStack.clear();
        Collections.addAll(responseCodeStack, responseCodes);
    }

    /**
     * Set expected url for the next N requests
     */
    public static void setExpectedUrls(String[] urls) {
        expectedUrlStack.clear();
        Collections.addAll(expectedUrlStack, urls);
    }

    public static void setRequestPayloads(String[] payloadReferences) {
        requestPayloadStack.clear();
        Collections.addAll(requestPayloadStack, payloadReferences);
    }

    @Override
    protected HttpURLConnection setupConnection(URL builtUrl) throws IOException {
        if (!expectedUrlStack.isEmpty()) {
            assertUrlsEqual(expectedUrlStack.remove(0), builtUrl.toString());
        }

        if (requestPayloadStack.isEmpty()) {
            return HttpURLConnectionMock.mockWithEmptyStream(builtUrl, responseCodeStack.remove(0));
        } else {
            String payloadReference = requestPayloadStack.remove(0);
            if (payloadReference == null) {
                return HttpURLConnectionMock.mockWithErroringStream(builtUrl, responseCodeStack.remove(0));
            } else {
                InputStream payloadStream;
                try {
                    payloadStream =
                            ReferenceManager._().DeriveReference(payloadReference).getStream();
                } catch (InvalidReferenceException ire) {
                    throw new IOException("No payload available at " + payloadReference);
                }
                return HttpURLConnectionMock.mockWithStream(builtUrl, responseCodeStack.remove(0), payloadStream);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void assertUrlsEqual(String expected, String request) {
        Uri requestUrl = Uri.parse(request);
        Uri expectedUrl = Uri.parse(expected);
        assertEquals(expectedUrl.getPath(), requestUrl.getPath());
        for (String queryParam : expectedUrl.getQueryParameterNames()) {
            assertEquals(requestUrl.getQueryParameter(queryParam), expectedUrl.getQueryParameter(queryParam));
        }
    }
}


