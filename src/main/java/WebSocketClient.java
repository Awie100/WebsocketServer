import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

class WebsocketMessage {
    int flags;
    byte[] payload;

    public WebsocketMessage(int flags, byte[] payload) {
        this.flags = flags;
        this.payload = payload;
    }
}

public abstract class WebSocketClient extends ClientSocket{

    UUID id;
    final WebSocketServer wss;
    final Thread runningThread;
    boolean isRunning;
    Deque<WebsocketMessage> messageQueue;

    static final byte[] emptyPayload = new byte[0];

    public WebSocketClient(Socket clientSocket, UUID id, WebSocketServer wss) throws IOException {
        super(clientSocket);
        this.id = id;
        this.wss = wss;
        this.runningThread = new Thread(this::runThread);
        this.messageQueue = new ArrayDeque<>();
    }

    public void run() {
        isRunning = true;
        if(!runningThread.isAlive()) runningThread.start();
    }

    public void stop() {
        if(runningThread.isAlive()) isRunning = false;
    }

    byte readByte() throws IOException, WebsocketException {
        int b = in.read();
        if(b == -1) throw new WebsocketException("Reached End of InputStream");
        return (byte) b;
    }

    byte[] readMessage() throws IOException, WebsocketException {
        int length = readByte();
        if((length & 0x80) == 0) throw new WebsocketException("Not Encrypted.");

        length &= 0x7f;
        if(length == 127) throw new WebsocketException("Way too long frame Length.");
        if(length == 126) length = ((readByte() << 8) | (readByte() & 0xff)) & 0xffff;
        byte[] mask = new byte[] {readByte(), readByte(), readByte(), readByte()};

        byte[] payload = new byte[length];
        for (int i = 0; i < length; i++) {
            payload[i] = (byte) (readByte() ^ mask[i & 0x3]);
        }
        return payload;
    }

    void sendMessage(int flags, byte[] payload) throws IOException {
        messageQueue.add(new WebsocketMessage(flags, payload));
        if(messageQueue.size() > 1) return;

        while (!messageQueue.isEmpty()) {
            WebsocketMessage msg = messageQueue.peek();
            int payloadLength = msg.payload.length;

            out.write(msg.flags);
            int length = payloadLength > 125 ? 126 : payloadLength;
            out.write(length);

            if (length == 126) {
                out.write(payloadLength >> 8);
                out.write(payloadLength);
            }

            for (int i = 0; i < payloadLength; i++) {
                out.write(msg.payload[i]);
            }

            messageQueue.poll();
        }
    }

    void sendText(String text) throws IOException {
        sendMessage(0x81, text.getBytes(StandardCharsets.UTF_8));
    }

    void sendBytes(byte[] bytes) throws IOException {
        sendMessage(0x82, bytes);
    }

    void sendClose(int reason) throws IOException {
        sendMessage(0x88, new byte[] {(byte) (reason >> 8), (byte) reason});
    }

    abstract void onMessage(String msg) throws IOException;
    abstract void onBytes(byte[] bytes) throws IOException;
    abstract void onClose(byte[] payload) throws IOException;
    abstract void onPing() throws IOException;
    abstract void onPong() throws IOException;

    void handleControlFrame(byte opcode, byte[] payload) throws IOException, WebsocketException {
        switch (opcode) {
            case 0x8:
                onClose(payload);
                stop();
                break;
            case 0x9:
                onPing();
                sendMessage(0x8A, emptyPayload);
                break;
            case 0xA:
                onPong();
                break;
            default:
                throw new WebsocketException("Unknown control opcode.");
        }
    }

    void handleNonControlFrame(byte opcode, byte[] payload) throws WebsocketException, IOException {
        switch (opcode) {
            case 0x1:
                onMessage(new String(payload));
                break;
            case 0x2:
                onBytes(payload);
                break;
            default:
                throw new WebsocketException("Unknown non-control opcode.");
        }
    }

    void runThread() {
        try {
            try {
                //listen loop
                byte opcodeCache = 0;
                List<byte[]> payloadCache = new ArrayList<>();

                while (isRunning) {
                    byte flags = readByte();
                    boolean isFinal = (flags & 0x80) != 0;
                    byte opcode = (byte) (flags & 0xf);
                    byte[] payload = readMessage();

                    if(!isFinal && opcode != 0) {
                        opcodeCache = opcode;
                        payloadCache.add(payload);
                    } else if(!isFinal) {
                        payloadCache.add(payload);
                    } else if((opcode & 0x8) != 0){
                        //do control frame
                        handleControlFrame(opcode, payload);
                        //System.out.printf("Control frame: %x", opcode);
                    } else {
                        opcodeCache = opcodeCache != 0 ? opcodeCache : opcode;
                        payloadCache.add(payload);

                        //concatenate fragments
                        int totalLength = 0;
                        for (byte[] bytes : payloadCache) {
                            totalLength += bytes.length;
                        }

                        byte[] totalPayload = new byte[totalLength];

                        int startIndex = 0;
                        for (byte[] bytes: payloadCache) {
                            System.arraycopy(bytes, 0, totalPayload, startIndex, bytes.length);
                            startIndex += bytes.length;
                        }

                        //do operation
                        handleNonControlFrame(opcodeCache, totalPayload);

                        //reset
                        opcodeCache = 0;
                        payloadCache = new ArrayList<>();
                    }
                }

            } catch (WebsocketException e) {
                System.out.println(e.getMessage());
            }


            if(isRunning) sendClose(2000);
            isRunning = false;

            wss.clients.remove(id);
            close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("END");
    }


}
