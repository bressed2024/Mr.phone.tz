import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class MrPhoneServer {
  private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
  private static final String PASSWORD = System.getenv("MR_PHONE_ADMIN_PASSWORD");
  private static final String ORIGIN = System.getenv().getOrDefault("MR_PHONE_ALLOWED_ORIGIN", "http://localhost:5500");
  private static final Path DATA = Paths.get(System.getenv().getOrDefault("MR_PHONE_DATA_DIR", "data"));
  private static final Map<String, Long> SESSIONS = new HashMap<>();
  private static final Pattern IMAGE = Pattern.compile("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$");

  public static void main(String[] args) throws Exception {
    if (PASSWORD == null || PASSWORD.length() < 12) throw new IllegalStateException("Set MR_PHONE_ADMIN_PASSWORD to a password of at least 12 characters");
    Files.createDirectories(DATA);
    Files.createDirectories(DATA.resolve("uploads"));
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
    server.createContext("/api/login", MrPhoneServer::login);
    server.createContext("/api/logout", MrPhoneServer::logout);
    server.createContext("/api/products", MrPhoneServer::products);
    server.createContext("/api/upload", MrPhoneServer::upload);
    server.createContext("/api/feedback", MrPhoneServer::feedback);
    server.setExecutor(Executors.newFixedThreadPool(8));
    server.start();
    System.out.println("MR. PHONE TZ API listening on " + PORT);
  }

  private static void login(HttpExchange x) throws IOException {
    if (options(x)) return;
    if (!method(x, "POST")) return;
    String body = read(x);
    String supplied = field(body, "password");
    if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), PASSWORD.getBytes(StandardCharsets.UTF_8))) {
      send(x, 401, "{"error":"Invalid credentials"}");
      return;
    }
    String token = UUID.randomUUID().toString().replace("-", "");
    synchronized (SESSIONS) { SESSIONS.put(token, System.currentTimeMillis() + 86_400_000L); }
    send(x, 200, "{"token":"" + token + ""}");
  }

  private static void logout(HttpExchange x) throws IOException {
    if (options(x)) return;
    String token = x.getRequestHeaders().getFirst("Authorization");
    if (token != null) synchronized (SESSIONS) { SESSIONS.remove(token.replace("Bearer ", "")); }
    send(x, 204, "");
  }

  private static void products(HttpExchange x) throws IOException {
    if (options(x)) return;
    if ("GET".equals(x.getRequestMethod())) { send(x, 200, readFile("products.json", "[]")); return; }
    if (!authorized(x)) return;
    if ("POST".equals(x.getRequestMethod())) { append("products.json", read(x)); send(x, 201, "{"ok":true}"); return; }
    if ("DELETE".equals(x.getRequestMethod())) { send(x, 204, ""); return; }
    send(x, 405, "{"error":"Method not allowed"}");
  }

  private static void feedback(HttpExchange x) throws IOException {
    if (options(x)) return;
    if ("GET".equals(x.getRequestMethod())) { send(x, 200, readFile("feedback.json", "[]")); return; }
    if (!authorized(x)) return;
    if ("POST".equals(x.getRequestMethod())) { append("feedback.json", read(x)); send(x, 201, "{"ok":true}"); return; }
    send(x, 405, "{"error":"Method not allowed"}");
  }

  private static void upload(HttpExchange x) throws IOException {
    if (options(x)) return;
    if (!authorized(x)) return;
    String body = read(x);
    String image = field(body, "image");
    String name = field(body, "name");
    if (image == null || name == null || name.length() > 80 || !IMAGE.matcher(image).matches() || image.length() > 8_000_000) {
      send(x, 400, "{"error":"Invalid image or filename"}");
      return;
    }
    byte[] bytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
    String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
    Files.write(DATA.resolve("uploads").resolve(safe), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    send(x, 201, "{"file":"uploads/" + safe + ""}");
  }

  private static boolean authorized(HttpExchange x) throws IOException {
    String raw = x.getRequestHeaders().getFirst("Authorization");
    if (raw == null || !raw.startsWith("Bearer ")) { send(x, 401, "{"error":"Login required"}"); return false; }
    synchronized (SESSIONS) {
      Long expiry = SESSIONS.get(raw.substring(7));
      if (expiry != null && expiry > System.currentTimeMillis()) return true;
    }
    send(x, 401, "{"error":"Session expired"}");
    return false;
  }

  private static boolean method(HttpExchange x, String expected) throws IOException {
    if (expected.equals(x.getRequestMethod())) return true;
    send(x, 405, "{"error":"Method not allowed"}");
    return false;
  }

  private static boolean options(HttpExchange x) throws IOException {
    if (!"OPTIONS".equals(x.getRequestMethod())) return false;
    send(x, 204, "");
    return true;
  }

  private static String field(String json, String key) {
    String needle = """ + key + """;
    int p = json.indexOf(needle);
    if (p < 0) return null;
    p = json.indexOf(':', p) + 1;
    while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
    if (p >= json.length() || json.charAt(p) != '"') return null;
    int end = json.indexOf('"', p + 1);
    return end < 0 ? null : json.substring(p + 1, end);
  }

  private static String read(HttpExchange x) throws IOException {
    if (x.getRequestBody().available() > 8_000_000) throw new IOException("Request too large");
    return new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static String readFile(String name, String fallback) throws IOException {
    Path p = DATA.resolve(name);
    return Files.exists(p) ? Files.readString(p) : fallback;
  }

  private static void append(String name, String item) throws IOException {
    String old = readFile(name, "[]").trim();
    if (!old.startsWith("[") || !old.endsWith("]")) old = "[]";
    String next = old.length() <= 2 ? "[" + item + "]" : old.substring(0, old.length() - 1) + "," + item + "]";
    Files.writeString(DATA.resolve(name), next, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void send(HttpExchange x, int status, String body) throws IOException {
    x.getResponseHeaders().set("Access-Control-Allow-Origin", ORIGIN);
    x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
    x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    x.sendResponseHeaders(status, body.isEmpty() ? -1 : bytes.length);
    if (!body.isEmpty()) try (OutputStream out = x.getResponseBody()) { out.write(bytes); }
  }
}
