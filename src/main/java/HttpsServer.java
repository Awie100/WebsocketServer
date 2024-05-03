import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;

public class HttpsServer extends HttpServer {

    static ServerSocket getServerSocket(InetSocketAddress address, String keyPath, String keySecret) throws Exception {
        char[] keyStorePass = keySecret.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(Path.of(keyPath).toFile()), keyStorePass);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, keyStorePass);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        ServerSocket ss = sslContext.getServerSocketFactory().createServerSocket(address.getPort(), 0, address.getAddress());
        Arrays.fill(keyStorePass, '0');
        return ss;
    }
    public HttpsServer(int port, String resPath, String keyPath, String keySecret) throws Exception {
        super(getServerSocket(new InetSocketAddress(port), keyPath, keySecret), resPath);
    }
}
