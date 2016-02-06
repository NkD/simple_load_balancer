package cz.nkd.cube;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Michal Nikodim (michal.nikodim@topmonks.com)
 */
public class SimpleLoadBalancerServlet implements Servlet {

    private ServletConfig config;
    private String[] serverUri;
    private AtomicInteger serverIndex = new AtomicInteger(-1);
    private HttpClient httpClient;
    protected HashSet<String> dontCopyHeaders = new HashSet<String>();

    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        initServers("http://localhost:8081/webapi,http://localhost:8082/webapi,http://localhost:8083/webapi,http://localhost:8084/webapi");
        initHttpClient();
        initDontCopyHeaders();
    }

    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) res;

        String destinationUrl = getDestinationUrl(httpReq);

        HttpRequestBase request = createHttpRequest(httpReq.getMethod(), destinationUrl);
        @SuppressWarnings("rawtypes")
        Enumeration headerNames = httpReq.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            String headerValue = httpReq.getHeader(headerName);
            if (!dontCopyHeaders.contains(headerName.toLowerCase())) {
                request.addHeader(headerName, headerValue);
            }
        }

        if (request instanceof HttpEntityEnclosingRequestBase && httpReq.getInputStream() != null) {
            HttpEntityEnclosingRequestBase base = (HttpEntityEnclosingRequestBase) request;
            HttpEntity entity = new InputStreamEntity(httpReq.getInputStream(), httpReq.getContentLength(), ContentType.create(httpReq.getContentType()));
            base.setEntity(entity);
        }

        HttpResponse response = null;
        try {
            response = httpClient.execute(request, HttpClientContext.create());
            httpResp.setStatus(response.getStatusLine().getStatusCode());
            Header[] allHeaders = response.getAllHeaders();
            for (Header header : allHeaders) {
                httpResp.addHeader(header.getName(), header.getValue());
            }
            httpResp.setHeader("balancer", destinationUrl);
            byte[] byteArray = EntityUtils.toByteArray(response.getEntity());
            httpResp.getOutputStream().write(byteArray);
            httpResp.flushBuffer();
            //copyStream(response.getEntity(), httpResp.getOutputStream());
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    private HttpRequestBase createHttpRequest(String method, String completeUrl) {
        if ("GET".equals(method)) {
            return new HttpGet(completeUrl);
        } else if ("POST".equals(method)) {
            return new HttpPost(completeUrl);
        } else if ("PUT".equals(method)) {
            return new HttpPut(completeUrl);
        } else if ("DELETE".equals(method)) {
            return new HttpDelete(completeUrl);
        } else if ("OPTIONS".equals(method)) {
            return new HttpOptions(completeUrl);
        } else if ("HEAD".equals(method)) {
            return new HttpHead(completeUrl);
        } else if ("TRACE".equals(method)) {
            return new HttpTrace(completeUrl);
        } else {
            throw new RuntimeException("Unknown http method " + method);
        }
    }
    
    private void copyStream(HttpEntity entity, OutputStream output) throws IOException {
        final InputStream instream = entity.getContent();
        if (instream != null) {
            try {
                final byte[] tmp = new byte[4096];
                int l;
                while ((l = instream.read(tmp)) != -1) {
                    output.write(tmp, 0, l);
                }
            } finally {
                instream.close();
                output.flush();
            }
        }
    }

    private String getDestinationUrl(HttpServletRequest request) {
        String incommingUrl = request.getPathInfo();
        String incommingQuery = request.getQueryString();
        int i = serverIndex.incrementAndGet() % serverUri.length;
        StringBuilder sb = new StringBuilder(serverUri[i]);
        if (!"/".equals(incommingUrl)) {
            sb.append(incommingUrl);
        }
        if (incommingQuery != null) {
            sb.append("?").append(incommingQuery);
        }
        return sb.toString();
    }

    private void initServers(String servers) {
        serverUri = servers.split(",");
        for (int i = 0; i < serverUri.length; i++) {
            serverUri[i] = serverUri[i].trim();
        }
    }

    private void initHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] { new X509TrustManager() {

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    //trusted
                }

                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    //trusted
                }
            } }, null);
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory> create();
            registryBuilder.register("http", PlainConnectionSocketFactory.getSocketFactory());
            registryBuilder.register("https", sslSocketFactory);
            Registry<ConnectionSocketFactory> registry = registryBuilder.build();

            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
            connectionManager.setDefaultMaxPerRoute(50);
            connectionManager.setMaxTotal(200);

            HttpClientBuilder builder = HttpClientBuilder.create();
            builder.setConnectionManager(connectionManager);
            builder.disableAutomaticRetries();
            builder.disableContentCompression();
            builder.disableCookieManagement();
            builder.disableRedirectHandling();
            builder.disableContentCompression();

            httpClient = builder.build();
        } catch (Throwable e) {
            throw new RuntimeException("HttpClient initialization failed", e);
        }
    }

    private void initDontCopyHeaders() {
        //dontCopyHeaders.add("proxy-connection");
        //dontCopyHeaders.add("connection");
        //dontCopyHeaders.add("host");
        dontCopyHeaders.add("content-length");
        dontCopyHeaders.add("content-type");
        //dontCopyHeaders.add("keep-alive");
        //dontCopyHeaders.add("transfer-encoding");
        //dontCopyHeaders.add("te");
        //dontCopyHeaders.add("trailer");
        //dontCopyHeaders.add("proxy-authorization");
        //dontCopyHeaders.add("proxy-authenticate");
        //dontCopyHeaders.add("upgrade");
    }

    public String getServletInfo() {
        return "simple-load-balancer";
    }

    public ServletConfig getServletConfig() {
        return config;
    }

    public void destroy() {
        //nothing
    }
}
