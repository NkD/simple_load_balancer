package cz.nkd.cube;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Michal Nikodim (michal.nikodim@topmonks.com)
 */
public class SimpleLoadBalancerServlet implements Servlet {

    private ServletConfig config;
    private String[] url;
    private AtomicInteger index = new AtomicInteger(-1);
    private CloseableHttpClient httpClient;

    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        String urls = config.getInitParameter("urls");
        url = urls.split(",");

        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(10);
        manager.setDefaultMaxPerRoute(10);
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setConnectionManager(manager);
        this.httpClient = builder.build();
    }

    public ServletConfig getServletConfig() {
        return config;
    }

    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        String incommingUrl = request.getPathInfo();
        String incommingQuery = request.getQueryString();
        int i = index.incrementAndGet() % url.length;
        String outgoingUrl = url[i] + ("/".equals(incommingUrl) ? "" : incommingUrl) + (incommingQuery != null ? "?" + incommingQuery : "");

        System.out.println(outgoingUrl);

        HttpUriRequest httpUriRequest = createMessage(method, outgoingUrl);
        copyHeaders(request, httpUriRequest);

        if (httpUriRequest instanceof HttpEntityEnclosingRequestBase) {
            InputStreamEntity httpEntity = new InputStreamEntity(req.getInputStream());
            httpEntity.setContentType(request.getContentType());
            HttpEntityEnclosingRequestBase hur = (HttpEntityEnclosingRequestBase) httpUriRequest;
            hur.setEntity(httpEntity);
        }

        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(httpUriRequest);
            response.setHeader("balancer", outgoingUrl);
            Header[] allHeaders = httpResponse.getAllHeaders();
            for (Header header : allHeaders) {
                response.setHeader(header.getName(), header.getValue());
            }
            response.setContentLength((int) httpResponse.getEntity().getContentLength());
            response.setContentType(httpResponse.getEntity().getContentType().getValue());
            copyStream(httpResponse.getEntity().getContent(), response.getOutputStream());
            response.setStatus(httpResponse.getStatusLine().getStatusCode());
            response.flushBuffer();
        } finally {
            if (httpResponse != null) httpResponse.close();
        }

    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private HttpUriRequest createMessage(HttpMethod httpMethod, String url) {
        switch (httpMethod) {
            case GET:
                return new HttpGet(url);
            case POST:
                return new HttpPost(url);
            case PUT:
                return new HttpPut(url);
            case DELETE:
                return new HttpDelete(url);
            case OPTIONS:
                return new HttpOptions(url);
            case HEAD:
                return new HttpHead(url);
            case TRACE:
                return new HttpTrace(url);
        }
        throw new RuntimeException("Unknown method " + httpMethod);
    }

    @SuppressWarnings("unchecked")
    private void copyHeaders(HttpServletRequest request, HttpRequest httpRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!"content-length".equalsIgnoreCase(name)) {
                String value = request.getHeader(name);
                httpRequest.addHeader(name, value);
            }
        }
    }

    public String getServletInfo() {
        return "simple-load-balancer";
    }

    public void destroy() {
        //nothing
    }

}
