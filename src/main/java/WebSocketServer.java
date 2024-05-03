import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WebSocketServer {
    Map<UUID, WebSocketClient> clients;
    WSCFactory clientFactory;
    Random rand;

    final static byte[] upgradeResponse = "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-Websocket-Accept: ".getBytes(StandardCharsets.UTF_8);
    final static byte[] endResponse = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    public WebSocketServer(WSCFactory clientFactory) {
        this.clientFactory = clientFactory;
        clients = new HashMap<>();
        rand = new Random();
    }

    static byte[] encodeKey(String key) throws HttpParseException {
        try {
            byte[] hexString = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hexString).getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new HttpParseException(500, "Cannot compute accept key.");
        }
    }

    void handleUpgrade(UUID id, ClientSocket clientSocket, Map<String, Set<String>> headers) throws HttpParseException, IOException {
        //headers.forEach((key, value) -> System.out.println(key + ": " + value));
        if(!headers.containsKey("Connection") || !headers.get("Connection").contains("Upgrade")) throw new HttpParseException(400, "Connection Header Bad.");
        if(!headers.containsKey("Upgrade") || !headers.get("Upgrade").contains("websocket")) throw new HttpParseException(400, "Upgrade Header Bad.");
        if(!headers.containsKey("Sec-WebSocket-Version") || !headers.get("Sec-WebSocket-Version").contains("13")) throw new HttpParseException(400, "Version Header Bad.");
        if(!headers.containsKey("Sec-WebSocket-Key")) throw new HttpParseException(400, "No Websocket Key");
        byte[] accept = WebSocketServer.encodeKey(headers.get("Sec-WebSocket-Key").toArray(String[]::new)[0]);
        clientSocket.out.write(upgradeResponse);
        clientSocket.out.write(accept);
        clientSocket.out.write(endResponse);
        WebSocketClient wsc = clientFactory.build(clientSocket.socket, id, this);
        if(clients.containsKey(id)) wsc.sendClose(1000);
        clients.put(id, wsc);
        wsc.run();
    }

    void handleUpgrade(ClientSocket clientSocket, Map<String, Set<String>> headers) throws IOException, HttpParseException {
        handleUpgrade(UUID.randomUUID(), clientSocket, headers);
    }

    void send(UUID id, String msg) throws IOException {
        if(!clients.containsKey(id)) {
            System.out.printf("no id %s", id);
            return;
        };
        System.out.println("Sending " + msg);
        clients.get(id).sendText(msg);
    }

    void sendAll(String msg) throws IOException {
        for(WebSocketClient client : clients.values()) {
            client.sendText(msg);
        }
    }

    void sendElse(UUID id, String msg) throws IOException {
        for(WebSocketClient client : clients.values()) {
            if(!client.id.equals(id)) client.sendText(msg);
        }
    }

    void close() {
        for(WebSocketClient client : clients.values()) {
            client.stop();
        }
    }

}
