import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

class RtcWSC extends WebSocketClient {

    static final String SEPARATOR = "::::";

    String addMsg, closeMsg;

    public RtcWSC(Socket clientSocket, UUID id, WebSocketServer wss) throws IOException {
        super(clientSocket, id, wss);
        addMsg = String.join(SEPARATOR, new String[] {"add_peer", id.toString()});
        closeMsg = String.join(SEPARATOR, new String[] {"remove_peer", id.toString()});
    }

    @Override
    void onMessage(String msg) throws IOException {
        //System.out.printf("%s: '%s'.\n", id, msg);

        String[] event = msg.split(SEPARATOR);
        if(event.length < 1) return;
        if(event[0].equals("join")) {
            wss.sendElse(id, addMsg);
            return;
        }

        if(event.length < 2) return;
        UUID peerId = UUID.fromString(event[1]);
        if(event.length < 3) {
            wss.send(peerId, closeMsg);
            return;
        }

        String eventResId = switch (event[0]) {
            case "offer" -> "get_offer";
            case "answer" -> "get_answer";
            case "candidate" -> "candidate";
            default -> null;
        };

        if(eventResId == null) {
            System.out.printf("%s: Unknown event '%s'.\n", id, event[0]);
            return;
        }

        String eventMsg = String.join(SEPARATOR, new String[] {eventResId, id.toString(), event[2]});
        wss.send(peerId, eventMsg);
    }

    @Override
    void onBytes(byte[] bytes) throws IOException {
        //never hit (if i did it right)
    }

    @Override
    void onClose(byte[] payload) throws IOException {
        wss.sendElse(id, closeMsg);
        System.out.printf("%s disconnected.\n", id);
    }

    @Override
    void onPing() throws IOException {
        System.out.printf("%s: Ping!\n", id);
    }

    @Override
    void onPong() throws IOException {
        System.out.printf("%s: Pong!\n", id);
    }
}


public class RtcWSCFactory implements WSCFactory{
    @Override
    public WebSocketClient build(Socket socket, UUID id, WebSocketServer wss) throws IOException {
        return new RtcWSC(socket, id, wss);
    }
}
