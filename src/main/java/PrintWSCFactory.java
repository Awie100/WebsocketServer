import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

class PrintWSC extends WebSocketClient{

    public PrintWSC(Socket clientSocket, UUID id, WebSocketServer wss) throws IOException {
        super(clientSocket, id, wss);
    }

    @Override
    void onMessage(String msg) throws IOException {
        System.out.printf("%4s: %s\n", id, msg);
        wss.sendAll(msg);
    }

    @Override
    void onBytes(byte[] bytes) {
        System.out.printf("%4s: ", id);
        for (byte b : bytes) {
            System.out.printf("%x", b);
        }
        System.out.println();
    }

    @Override
    void onClose(byte[] payload) {
        System.out.printf("%4s: Closed!\n", id);
    }

    @Override
    void onPing() {
        System.out.printf("%4s: Ping!\n", id);
    }

    @Override
    void onPong() {
        System.out.printf("%4s: Pong!\n", id);
    }
}

public class PrintWSCFactory implements WSCFactory{

    @Override
    public WebSocketClient build(Socket socket, UUID id, WebSocketServer wss) throws IOException {
        return new PrintWSC(socket, id, wss);
    }
}
