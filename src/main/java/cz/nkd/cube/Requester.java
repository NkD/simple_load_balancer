package cz.nkd.cube;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

/**
 * @author Michal Nikodim (michal.nikodim@topmonks.com)
 */
public class Requester {

    public static void main(String[] args) throws ParseException, IOException {
        HttpRequestBase request = new HttpGet("http://localhost:8081/webapi/v1/redirect/close");
        request.addHeader("web-api-key", "92baf480-34a8-4219-8187-b2ce83d7ea94");

        if (request instanceof HttpEntityEnclosingRequestBase) {
            HttpEntityEnclosingRequestBase base = (HttpEntityEnclosingRequestBase) request;
            byte[] bytes = "{\"test\" : \"data\"}".getBytes();
            HttpEntity entity = new InputStreamEntity(new ByteArrayInputStream(bytes), bytes.length, ContentType.create("application/json"));
            base.setEntity(entity);
        }
        HttpResponse response = null;
        try {
            response = HttpClientFactory.get().execute(request, HttpClientContext.create());
            System.out.println(response.getStatusLine().toString());
            Header[] allHeaders = response.getAllHeaders();
            for (Header header : allHeaders) {
                System.out.println("  " + header.getName() + ":" + header.getValue());
            }
            if (response.getEntity() != null) {
                String responseString = EntityUtils.toString(response.getEntity());
                System.out.println("PAYLOAD:");
                System.out.println(responseString);
            }

        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    public static class HttpDeleteWithPayload extends HttpEntityEnclosingRequestBase {
        public final static String METHOD_NAME = "DELETE";

        public HttpDeleteWithPayload() {
            super();
        }

        public HttpDeleteWithPayload(final URI uri) {
            super();
            setURI(uri);
        }

        /**
         * @throws IllegalArgumentException if the uri is invalid.
         */
        public HttpDeleteWithPayload(final String uri) {
            super();
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }

    }

}
