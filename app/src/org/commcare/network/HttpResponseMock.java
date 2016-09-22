package org.commcare.network;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpResponseMock {
    public static HttpResponse buildHttpResponseMock(final int statusCode, final InputStream entityStream) {
        return new HttpResponse() {

            private Map<String,Header> headers = new HashMap<>();

            private final StatusLine statusLine = new StatusLine() {
                @Override
                public ProtocolVersion getProtocolVersion() {
                    return null;
                }

                @Override
                public int getStatusCode() {
                    return statusCode;
                }

                @Override
                public String getReasonPhrase() {
                    return null;
                }
            };

            @Override
            public StatusLine getStatusLine() {
                return statusLine;
            }

            @Override
            public void setStatusLine(StatusLine statusLine) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setStatusCode(int i) throws IllegalStateException {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setReasonPhrase(String s) throws IllegalStateException {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HttpEntity getEntity() {
                return new HttpEntity() {
                    @Override
                    public boolean isRepeatable() {
                        return false;
                    }

                    @Override
                    public boolean isChunked() {
                        return false;
                    }

                    @Override
                    public long getContentLength() {
                        return 0;
                    }

                    @Override
                    public Header getContentType() {
                        return null;
                    }

                    @Override
                    public Header getContentEncoding() {
                        return null;
                    }

                    @Override
                    public InputStream getContent() throws IOException, IllegalStateException {
                        return entityStream;
                    }

                    @Override
                    public void writeTo(OutputStream outputStream) throws IOException {

                    }

                    @Override
                    public boolean isStreaming() {
                        return false;
                    }

                    @Override
                    public void consumeContent() throws IOException {

                    }
                };
            }

            @Override
            public void setEntity(HttpEntity httpEntity) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Locale getLocale() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setLocale(Locale locale) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public ProtocolVersion getProtocolVersion() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public boolean containsHeader(String s) {
                return false;
            }

            @Override
            public Header[] getHeaders(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header getFirstHeader(String s) {
                return headers.get(s);
            }

            @Override
            public Header getLastHeader(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public Header[] getAllHeaders() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void addHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void addHeader(String s, String s1) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setHeader(String s, String s1) {
                headers.put(s, new BasicHeader(s, s1));
            }

            @Override
            public void setHeaders(Header[] headers) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void removeHeader(Header header) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void removeHeaders(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HeaderIterator headerIterator() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HeaderIterator headerIterator(String s) {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public HttpParams getParams() {
                throw new RuntimeException("not supported in mock");
            }

            @Override
            public void setParams(HttpParams httpParams) {
                throw new RuntimeException("not supported in mock");
            }
        };
    }

    public static HttpResponse buildHttpResponseMockForAsyncRestore() {
        HttpResponse response = buildHttpResponseMock(202, null);
        response.setHeader("Retry-After", "2");
        return response;
    }

}
