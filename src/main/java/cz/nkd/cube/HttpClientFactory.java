package cz.nkd.cube;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author Michal Nikodim (michal.nikodim@topmonks.com)
 */
public class HttpClientFactory {

    private static HttpClient httpClient;
    
    static {
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
    
    public static HttpClient get(){
        return httpClient;
    }
    
    public static HttpRequestBase createHttpRequest(String method, String completeUrl) {
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
    
}
