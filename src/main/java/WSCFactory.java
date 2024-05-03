import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

public interface WSCFactory {
    WebSocketClient build(Socket socket, UUID id, WebSocketServer wss) throws IOException;
}
