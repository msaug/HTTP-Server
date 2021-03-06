///A Simple Web Server (WebServer.java)

package http.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Webserver implemented using Sockets.
 * Handles most popular HTTP Request such as GET, POST, etc.
 * Users can interact with the server by using a client such as postman
 * or in the browser at localhost:{PORT_NUMBER}.
 */
public class WebServer {


    private static final String AUTHORIZED_DIRECTORY = "doc";
    private static final String AUTHORIZED_USER_DIRECTORY = "doc/users/";
    private static final String INDEX_PATH = "doc/index.html";
    private static final String ERROR_PATH = "doc/404.html";
    private static final String HANDLE_REQUEST = "HandleRequest";


    /**
     * Start the application.
     *
     * @param args port to start the server on
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java EchoServer <EchoServer port>");
            System.exit(1);
        }
        try {
            int serverPort = Integer.parseInt(args[0]);
            System.out.println("Working Directory = " + System.getProperty("user.dir"));
            if (serverPort < 1024 || serverPort > 65535) {
                System.err.println("Error, the port must be an integer between 1024 and 65535");
                System.exit(1);
            }

            WebServer ws = new WebServer();
            ws.start(serverPort);
        } catch (NumberFormatException e) {
            System.err.println("Error, the port number must be an integer");
        }
    }

    /**
     * WebServer constructor.
     */
    protected void start(int port) {
        ServerSocket s;
        Socket client = null;

        System.out.println("Webserver starting up on port " + port);
        System.out.println("(press ctrl-c to exit)");
        try {
            // create the main server socket
            s = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return;
        }
        System.out.println("Waiting for connection");
        for (; ; ) {
            try {
                // wait for a connection
                client = s.accept();
                ClientHandler clientSock = new ClientHandler(client);
                new Thread(clientSock).start();
            } catch (Exception e1) {
                System.out.println("Error: " + e1);
                e1.printStackTrace();
            }
        }
    }

    /**
     * ClientHandler class implements Runnable because we want our
     * server to be able to handle multiple clients simultaneously with multithreading.
     */
    private static class ClientHandler implements Runnable {
        private final Socket client;
        // Constructor
        public ClientHandler(Socket socket) {
            this.client = socket;
        }

        /**
         * Function to run when our thread is started.
         */
        @Override
        public void run() {
            try {
                handleClient(this.client);
            } catch (Exception e1) {
                System.out.println("Error: " + e1);
                e1.printStackTrace();
                try {
                    sendHeader(client, "500 Internal Server Error");
                } catch (Exception e2) {
                }
                try {
                    client.close();
                } catch (Exception e) {
                }

            }
        }

        /**
         * Handles the client actions once there is a client connected.
         * Reads the request parameters and acts accordingly.
         * @param client Socket currently connected.
         * @throws IOException
         */
        private void handleClient(Socket client) throws IOException {
            BufferedInputStream in = new BufferedInputStream(client.getInputStream());
            String request = new String();

            //Reads all request parameters until a CRLF sequence
            int currentByte = '\0', prevByte = '\0';
            boolean newline = false;
            while ((currentByte = in.read()) != -1 && !(newline && prevByte == '\r' && currentByte == '\n')) {
                if (prevByte == '\r' && currentByte == '\n') {
                    newline = true;
                } else if (!(prevByte == '\n' && currentByte == '\r')) {
                    newline = false;
                }
                prevByte = currentByte;
                request += (char) currentByte;
            }

            System.out.println("request: " + request);
            if (request.isEmpty()) {
                sendHeader(client, "400 Bad Request");
                return;
            }

            /**
             * Parse the request parameters
             */
            String[] requestsLines = request.split("\r\n");
            String[] requestLine = requestsLines[0].split(" ");
            String method = requestLine[0];
            String filename = requestLine[1].substring(1, requestLine[1].length());
            String version = requestLine[2];
            String host = requestsLines[1].split(" ")[1];

            boolean requestToHandle = false;

            List<String> headers = new ArrayList<>();
            for (int h = 2; h < requestsLines.length - 1; h++) {
                String header = requestsLines[h];
                if (requestsLines[h].startsWith("Content-Type: application/x-www-form-urlencoded")) {
                    requestToHandle = true;
                }
                headers.add(header);
            }
            /**
             * If resource is empty, redirect to index file
             * If it's withing the authorized directory, call the corresponding method
             * Otherwise, access is forbidden for security purposes
             */
            try {
                if (headers.isEmpty() || method.isEmpty()) {
                    sendHeader(client, "400 Bad Request");
                    return;
                }

                if (filename.startsWith(HANDLE_REQUEST)) {
                    HandleRequest handleRequest = new HandleRequest();
                    if (method.equals("POST") && requestToHandle) {
                        handleRequest.doPOST(in, client);
                    } else {
                        sendHeader(client, "501 Not Implemented");
                    }

                } else if (filename.isEmpty()) {
                    if (method.equals("GET")) {
                        doGET(client, INDEX_PATH);
                    } else if (method.equals("HEAD")) {
                        doHEAD(client, INDEX_PATH);
                    } else if (method.equals("OPTIONS")) {
                        doOPTIONS(client, INDEX_PATH);
                    } else {
                        sendHeader(client, "403 Forbidden");
                    }

                } else if (filename.startsWith(AUTHORIZED_DIRECTORY)) {
                    if (method.equals("GET")) {
                        doGET(client, filename);
                    } else if (method.equals("POST")) {
                        doPOST(in, client, filename);
                    } else if (method.equals("PUT")) {
                        doPUT(in, client, filename);
                    } else if (method.equals("HEAD")) {
                        doHEAD(client, filename);
                    } else if (method.equals("DELETE")) {
                        doDELETE(client, filename);
                    } else if (method.equals("OPTIONS")) {
                        doOPTIONS(client, filename);
                    } else {
                        sendHeader(client, "501 Not Implemented");
                    }
                } else {
                    sendHeader(client, "403 Forbidden");
                }
            } catch (Exception e) {
                try {
                    e.printStackTrace();
                    sendHeader(client, "500 Internal Server Error");
                } catch (Exception e2) {
                }
            }
            in.close();
        }


        /**
         * Given a client and a filename to access, returns to the client the content of the file if it exists
         * or 404 otherwise.
         *
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doGET(Socket client, String filename) throws IOException {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                Path filePath = Paths.get(filename);
                String contentType = guessContentType(filePath);
                sendContentResponse(client, "200 OK", contentType, Files.readAllBytes(filePath), file.length());
            } else {
                sendHeader(client, "404 Not Found");
            }
        }

        /**
         * Given a client and a filename to access, returns to the client the headers
         * that a GET request would return.
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doHEAD(Socket client, String filename) throws IOException {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                Path filePath = Paths.get(filename);
                String contentType = guessContentType(filePath);
                sendHeader(client, "200 OK", contentType, file.length());
            } else {
                sendHeader(client, "404 Not Found");
            }
        }

        /**
         * handles the POST request.
         * Creates a resource if the specified file doesn't exist already.
         * Otherwise, appends the new information to the specified file.
         *
         * @param in
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doPOST(BufferedInputStream in, Socket client, String filename) throws IOException {
            File file = new File(filename);
            boolean appendMode = file.exists();
            //Output stream will be in append mode if the file exists, otherwise in the beginning
            BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file, appendMode));
            byte[] buffer = new byte[256];
            while (in.available() > 0) {
                int nbRead = in.read(buffer);
                fOut.write(buffer, 0, nbRead);
            }
            if (appendMode) {
                sendHeader(client, "200 OK");
            } else {
                sendHeader(client, "201 Created");
            }
            fOut.flush();
            fOut.close();
        }

        /**
         * Implementation of the HTTP PUT request method according to the specifications listed on
         * <a href=https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/PUT>the mozilla developer docs</a>
         *
         * @param in
         * @param client
         * @param filename
         */
        private void doPUT(BufferedInputStream in, Socket client, String filename) throws IOException {
            File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
            boolean exists = file.exists();
            PrintWriter writer = new PrintWriter(filename);
            writer.print("");
            writer.close();
            BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(file));

            byte[] buffer = new byte[256];
            while (in.available() > 0) {
                int nbRead = in.read(buffer);
                fOut.write(buffer, 0, nbRead);
            }
            if (exists) {
                sendHeader(client, "204 No Content");
            } else {
                sendHeader(client, "201 Created");
            }
            fOut.flush();
            fOut.close();
        }

        /**
         * Implementation of the HTTP DELETE method according to the specifications listed on
         * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/DELETE">the mozilla developer docs</a>
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doDELETE(Socket client, String filename) throws IOException {
            try {
                File file = new File(filename);//Output stream will be in append mode if the file exists, otherwise in the beginning
                boolean exists = file.exists();
                boolean deleted = false;
                if (file.exists() && file.isFile()) {
                    deleted = file.delete();
                }
                if (deleted) {
                    sendHeader(client, "204 No Content");
                } else if (!exists) {
                    sendHeader(client, "404 Not Found");
                } else {
                    sendHeader(client, "403 Forbidden");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Implementation of the HTTP DELETE method according to the specifications listed on
         * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS">the mozilla developer docs</a>
         * @param client
         * @param filename
         * @throws IOException
         */
        private void doOPTIONS(Socket client, String filename) throws IOException {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                sendHeader(client, "200 OK", "OPTIONS, GET, HEAD, POST, PUT, DELETE");
            } else {
                sendHeader(client, "404 Not Found");
            }
        }

        /**
         * Sends a response with only a header and the response status
         * @param client
         * @param status
         * @throws IOException
         */
        private static void sendHeader(Socket client, String status) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        /**
         * Sends a header with the response status the content type and the content length for the HEAD request.
         * @param client
         * @param status
         * @param contentType
         * @param length
         * @throws IOException
         */
        private static void sendHeader(Socket client, String status, String contentType, long length) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write(("Content-Type: " + contentType + "\r\n").getBytes());
            clientOutput.write(("Content-Length: " + length + "\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        /**
         * Sends a response with a header and a body for the GET request
         * @param client
         * @param status
         * @param contentType
         * @param content
         * @throws IOException
         */
        private static void sendContentResponse(Socket client, String status, String contentType, byte[] content, long length) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write(("Content-Type: " + contentType + "\r\n").getBytes());
            clientOutput.write(("Content-Length: " + length + "\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.write(content);
            clientOutput.write("\r\n\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        /**
         * Sends a header with the allowed http requests for the OPTIONS request
         * @param client
         * @param status
         * @param allows
         * @throws IOException
         */
        private static void sendHeader(Socket client, String status, String allows) throws IOException {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write(("HTTP/1.1 " + status + "\r\n").getBytes());
            clientOutput.write(("Allow: " + allows + "\r\n").getBytes());
            clientOutput.write(("Content-Length: 0" + "\r\n").getBytes());
            clientOutput.write("\r\n\r\n".getBytes());
            clientOutput.flush();
            clientOutput.close();
        }

        /**
         *
         * @param filePath path to the file
         * @return content type of the file
         * @throws IOException
         */
        private static String guessContentType(Path filePath) throws IOException {
            return Files.probeContentType(filePath);
        }
    }
}
