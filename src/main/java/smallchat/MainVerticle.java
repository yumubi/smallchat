package smallchat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainVerticle extends AbstractVerticle {

  private static final int MAX_CLIENTS = 1000;
  private static final int MAX_NICK_LEN = 32;

  private final Map<NetSocket, String> clients = new ConcurrentHashMap<>();

  @Override
  public void start() {
    NetServer server = vertx.createNetServer();

    server.connectHandler(socket -> {
      String initialMsg = "Welcome Simple Chat! Use /nick to change nick name.\n";
      socket.write(Buffer.buffer(initialMsg));

      String initialNick = "user" + socket.remoteAddress().port();
      clients.put(socket, initialNick);

      socket.handler(buffer -> {
        String message = buffer.toString().trim();

        if (message.startsWith("/")) {
          handleCommand(socket, message);
        } else {
          String nick = clients.get(socket);
          broadcastMessage(nick, message);
        }
      });

      socket.closeHandler(v -> {
        String nick = clients.remove(socket);
        if (nick != null) {
          System.out.println("Client left: " + nick);
          broadcastMessage("Server", nick + " left the chat");
        }
      });
    });

    server.listen(8972, result -> {
      if (result.succeeded()) {
        System.out.println("Server started on port 8972");
      } else {
        System.err.println("Failed to start the server: " + result.cause().getMessage());
      }
    });
  }

  private void handleCommand(NetSocket socket, String command) {
    String[] parts = command.split(" ", 2);
    String cmd = parts[0];

    if (cmd.equals("/nick") && parts.length > 1) {
      String newNick = parts[1].trim();
      if (newNick.length() <= MAX_NICK_LEN) {
        String oldNick = clients.get(socket);
        clients.put(socket, newNick);
        socket.write(Buffer.buffer("Your nickname has been changed to: " + newNick + "\n"));
        broadcastMessage("Server", oldNick + " changed nickname to " + newNick);
      } else {
        socket.write(Buffer.buffer("Nickname is too long. Maximum length is " + MAX_NICK_LEN + " characters.\n"));
      }
    } else {
      socket.write(Buffer.buffer("Unknown command: " + cmd + "\n"));
    }
  }

  private void broadcastMessage(String nick, String message) {
    clients.forEach((clientSocket, clientNick) -> {
      if (!clientSocket.equals(clientNick)) {
        clientSocket.write(Buffer.buffer(nick + ": " + message + "\n"));
      }
    });
  }

}
