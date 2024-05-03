import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(new File(".").getAbsoluteFile());
        HttpServer server = new HttpServer(80,"./resources");
        WebSocketServer wss = new WebSocketServer(new RtcWSCFactory());

        server.addPath("/", (bundle) -> {
            if(!bundle.method.equalsIgnoreCase("get")) throw new HttpParseException(404, "Not Found.");
            server.httpSendFile("index.html", bundle.client);
            //System.out.println("Sent Index File");
            bundle.client.close();
        });

        server.addPath("/upgrade", (bundle) -> {
            if(!bundle.method.equalsIgnoreCase("get")) throw new HttpParseException(404, "Not Found.");
            wss.handleUpgrade(bundle.client, bundle.headers);
            //System.out.println("Upgraded new WebSocket.");
        });

        server.run();
    }
}
