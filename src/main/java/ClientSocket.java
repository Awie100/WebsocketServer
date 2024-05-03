import java.io.*;
import java.net.Socket;

public class ClientSocket {

    Socket socket;
    InputStream in;
    OutputStream out;

    public ClientSocket(Socket clientSocket) throws IOException {
        this.socket = clientSocket;
        this.in = clientSocket.getInputStream();
        this.out = clientSocket.getOutputStream();
    }

    void close() throws IOException {
        out.close();
        in.close();
        socket.close();
    }
}
