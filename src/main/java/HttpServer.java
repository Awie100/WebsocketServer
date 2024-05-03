import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HttpBundle {
    ClientSocket client;
    String method;
    MatchResult path;
    Map<String, Set<String>> headers;
    byte[] body;

    public HttpBundle(ClientSocket client, String method, MatchResult path, Map<String, Set<String>> headers, byte[] body) {
        this.client = client;
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
    }
}

interface HttpPathHandler {
    void run(HttpBundle bundle) throws IOException, HttpParseException;
}

public class HttpServer {
    final ServerSocket server;
    final ExecutorService requestHandlerPool;
    final Thread runningThread;
    boolean isRunning;
    final Path resourcesPath;
    Map<Pattern, HttpPathHandler> pathHandlers;

    HttpPathHandler defaultHandler;

    final static Pattern requestLine = Pattern.compile("^([!-~]+) (/[!-~]*) HTTP/1\\.1$");
    final static Pattern headerLine = Pattern.compile("([!#$%&'*+\\-.^`|~\\w]+):\\s*([!-~]+([ \\t][!-~]+)*)");
    final static Pattern anyChars = Pattern.compile(".*");

    public HttpServer(int port, String resPath) throws IOException {
        this(new ServerSocket(port), resPath);
    }

    public HttpServer(ServerSocket ss, String resPath) {
        server = ss;
        requestHandlerPool = Executors.newFixedThreadPool(20);
        runningThread = new Thread(this::runThread);
        isRunning = false;

        resourcesPath = Paths.get(resPath).normalize();
        pathHandlers = new LinkedHashMap<>();
        defaultHandler = (bundle) -> {
            if(!bundle.method.equalsIgnoreCase("get")) throw new HttpParseException(404, "Not Found.");
            httpSendFile(bundle.path.group(), bundle.client);
            bundle.client.close();
        };
    }

    public void addPath(String path, HttpPathHandler pathHandler) {
        pathHandlers.put(Pattern.compile(path), pathHandler);
    }

    public boolean removePath(String path) {
        Pattern pathPattern = Pattern.compile(path);
        if(!pathHandlers.containsKey(pathPattern)) return false;
        pathHandlers.remove(pathPattern);
        return true;
    }

    public void setDefaultPath(HttpPathHandler handler) {
        this.defaultHandler = handler;
    }

    String getFileType(String fileName) {
        if(fileName.endsWith(".html")) return "text/html";
        if(fileName.endsWith(".js")) return "text/javascript";
        if(fileName.endsWith(".css")) return "text/css";
        if(fileName.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }

    public void httpSendFile(String fileName, ClientSocket client) throws HttpParseException, IOException {
        Path filePath = Paths.get(resourcesPath.toString(), fileName).normalize();
        if(!filePath.startsWith(resourcesPath)) throw new HttpParseException(400, "File not in Resources Directory.");
        if(!Files.exists(filePath) || Files.isDirectory(filePath)) throw new HttpParseException(404, "File does not Exist");
        long fileLength = filePath.toFile().length();
        InputStream file = new FileInputStream(filePath.toFile());

        client.out.write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
        client.out.write(String.format("Content-Type: %s\r\n", getFileType(filePath.toString())).getBytes(StandardCharsets.UTF_8));
        client.out.write(String.format("Content-Length: %d\r\n\r\n", fileLength).getBytes(StandardCharsets.UTF_8));

        byte[] buffer = new byte[1024];
        int len;
        while ((len = file.read(buffer)) != -1) {
            client.out.write(buffer, 0, len);
        }

        file.close();
    }

    void handleHttpRequest(ClientSocket client) throws IOException, HttpParseException {
        BufferedReader inBuf = new BufferedReader(new InputStreamReader(client.in));
        Map<String, Set<String>> headers = new HashMap<>();

        String line = inBuf.readLine();
        Matcher requestMatcher = requestLine.matcher(line);
        if(!requestMatcher.matches()) throw new HttpParseException(400, "This is not a HTTP Request");

        //System.out.println("Got Request!");
        System.out.println(line);

        String method = requestMatcher.group(1);
        String path = requestMatcher.group(2);

        while (!(line = inBuf.readLine()).isEmpty()) {
            Matcher headerMatcher = headerLine.matcher(line);
            if(!headerMatcher.matches()) throw new HttpParseException(400, "Headers are wrong Format.");
            headers.put(headerMatcher.group(1), new HashSet<>(List.of(headerMatcher.group(2).split(", "))));
        }

        //System.out.println("Parsed Headers!");

        byte[] body = null;
        if(headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(headers.get("Content-Length").toArray(String[]::new)[0]);
            body = new byte[length];
            int readLength = client.in.read(body);
            if(readLength == -1) throw new HttpParseException(400, "Body is wrong Length");
            //System.out.println("Parsed Body!");
        }
        for(Pattern pathPattern : pathHandlers.keySet()) {
            Matcher pathMatch = pathPattern.matcher(path);
            if(pathMatch.matches()) {
                HttpBundle bundle = new HttpBundle(client, method, pathMatch, headers, body);
                pathHandlers.get(pathPattern).run(bundle);
                return;
            }
        }

        if(defaultHandler != null) {
            Matcher allMatch = anyChars.matcher(path);
            if(!allMatch.matches()) throw new HttpParseException(404, "Bad Path");
            HttpBundle bundle = new HttpBundle(client, method, allMatch, headers, body);
            defaultHandler.run(bundle);
        }

        throw new HttpParseException(404, String.format("Bad Path '%s'", path));
    }

    void httpThread(ClientSocket client) {
        try {
            try {
                handleHttpRequest(client);
            } catch (HttpParseException e) {
                client.out.write(String.format("HTTP/1.1 %d %s\r\n\r\n", e.status, e.getMessage()).getBytes(StandardCharsets.UTF_8));
                client.close();
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    void runThread() {
        while (isRunning) {
            try {
                ClientSocket client = new ClientSocket(server.accept());
                requestHandlerPool.execute(() -> httpThread(client));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        isRunning = true;
        if(!runningThread.isAlive()) runningThread.start();
    }

    public void stop() {
        if(runningThread.isAlive()) isRunning = false;
    }
}
