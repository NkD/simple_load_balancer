package cz.nkd.cube;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Michal Nikodim (michal.nikodim@topmonks.com)
 */
public class SimpleLoadBalancerServlet implements Servlet {

    private ServletConfig config;
    private String[] serverUri;
    private AtomicInteger serverIndex = new AtomicInteger(-1);
    private static final Set<String> dontCopyHeaders = new HashSet<String>() {
        private static final long serialVersionUID = 1L;

        {
            add("content-length");
            add("content-type");
            add("host");
            add("cache-control");
            add("connection");
            add("date");
            add("pragma");
            add("trailer");
            add("transfer-encoding");
            add("upgrade");
            add("via");
            add("warning");
        }
    };
    
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        initServers("http://localhost:8081/webapi,http://localhost:8082/webapi,http://localhost:8083/webapi,http://localhost:8084/webapi");
    }

    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) res;

        String destinationUrl = getDestinationUrl(httpReq);

        HttpRequestBase request = HttpClientFactory.createHttpRequest(httpReq.getMethod(), destinationUrl);
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
            response = HttpClientFactory.get().execute(request, HttpClientContext.create());
            httpResp.setStatus(response.getStatusLine().getStatusCode());
            Header[] allHeaders = response.getAllHeaders();
            for (Header header : allHeaders) {
                httpResp.addHeader(header.getName(), header.getValue());
            }
            httpResp.setHeader("balancer", destinationUrl);
            copyStream(response.getEntity(), httpResp.getOutputStream());
        } finally {
            HttpClientUtils.closeQuietly(response);
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
