package cl.pedidos360.bff;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Microservicio falso sobre HTTP real, para probar el BFF como proxy sin levantar los servicios. */
final class FakeDownstream implements AutoCloseable {
  private static final String CREATED_BODY = "{\"id\":\"OT-NEW\"}";

  private final HttpServer server;
  private final AtomicReference<String> lastCreateBody = new AtomicReference<>();
  private final AtomicReference<String> lastCreateContentType = new AtomicReference<>();
  private final AtomicReference<String> lastCreateCallerName = new AtomicReference<>();
  private final AtomicInteger createCalls = new AtomicInteger();
  private final Map<String, String> accessStatusByUser = new ConcurrentHashMap<>();
  private final AtomicReference<String> lastLookupUserId = new AtomicReference<>();
  private final AtomicReference<String> lastAccessRequestBody = new AtomicReference<>();
  private final AtomicReference<String> lastDecisionBody = new AtomicReference<>();
  private final AtomicInteger lookupCalls = new AtomicInteger();
  private volatile int createStatus = 201;
  private volatile String createBody = CREATED_BODY;
  private volatile int eventsStatus = 200;
  private volatile Duration eventsDelay = Duration.ZERO;
  private volatile int accessLookupStatus = 200;

  private FakeDownstream(HttpServer server) {
    this.server = server;
  }

  static FakeDownstream start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      // Varios hilos: una respuesta lenta no debe bloquear a las demas pruebas.
      server.setExecutor(Executors.newCachedThreadPool());
      FakeDownstream fake = new FakeDownstream(server);
      server.createContext("/internal/work-orders", fake::workOrders);
      server.createContext("/internal/events", fake::events);
      server.createContext("/internal/access-requests", fake::accessRequests);
      server.start();
      return fake;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  String lastCreateBody() {
    return lastCreateBody.get();
  }

  String lastCreateContentType() {
    return lastCreateContentType.get();
  }

  /** Valor (codificado como URL) del encabezado con el nombre de quien emite la cotizacion. */
  String lastCreateCallerName() {
    return lastCreateCallerName.get();
  }

  int createCalls() {
    return createCalls.get();
  }

  void setCreateResponse(int status, String body) {
    this.createStatus = status;
    this.createBody = body;
  }

  void setEventsStatus(int status) {
    this.eventsStatus = status;
  }

  void setEventsDelay(Duration delay) {
    this.eventsDelay = delay;
  }

  /** Estado de acceso de un usuario (PENDING, APPROVED, REJECTED); sin configurar responde NONE. */
  void setAccessStatus(String userId, String status) {
    accessStatusByUser.put(userId, status);
  }

  /** Fuerza el codigo con que responde la consulta de acceso de un usuario (por ejemplo 500). */
  void setAccessLookupStatus(int status) {
    this.accessLookupStatus = status;
  }

  int lookupCalls() {
    return lookupCalls.get();
  }

  String lastLookupUserId() {
    return lastLookupUserId.get();
  }

  String lastAccessRequestBody() {
    return lastAccessRequestBody.get();
  }

  String lastDecisionBody() {
    return lastDecisionBody.get();
  }

  /** Devuelve el servicio a su comportamiento normal; llamar antes de cada prueba. */
  void reset() {
    createStatus = 201;
    createBody = CREATED_BODY;
    eventsStatus = 200;
    eventsDelay = Duration.ZERO;
    accessLookupStatus = 200;
    createCalls.set(0);
    lookupCalls.set(0);
    accessStatusByUser.clear();
    lastCreateBody.set(null);
    lastCreateContentType.set(null);
    lastCreateCallerName.set(null);
    lastLookupUserId.set(null);
    lastAccessRequestBody.set(null);
    lastDecisionBody.set(null);
  }

  private void workOrders(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      respond(exchange, 200, "[{\"id\":\"OT-1\"}]");
      return;
    }
    createCalls.incrementAndGet();
    lastCreateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    lastCreateContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
    lastCreateCallerName.set(exchange.getRequestHeaders().getFirst("X-Caller-Name"));
    respond(exchange, createStatus, createBody);
  }

  private void events(HttpExchange exchange) throws IOException {
    sleep(eventsDelay);
    respond(exchange, eventsStatus, eventsStatus == 200
        ? "[{\"id\":1,\"eventType\":\"OtCreada\"}]"
        : "{\"trace\":\"java.lang.NullPointerException\"}");
  }

  private void accessRequests(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    if (path.endsWith("/me")) {
      String userId = queryParam(exchange, "userId");
      lookupCalls.incrementAndGet();
      lastLookupUserId.set(userId);
      if (accessLookupStatus != 200) {
        respond(exchange, accessLookupStatus, "{\"trace\":\"java.lang.IllegalStateException\"}");
        return;
      }
      respond(exchange, 200, "{\"status\":\"" + accessStatusByUser.getOrDefault(userId, "NONE")
          + "\",\"unknownField\":true}");
    } else if (path.endsWith("/decision")) {
      lastDecisionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      respond(exchange, 200, "{\"id\":7,\"status\":\"APPROVED\"}");
    } else if ("POST".equals(method)) {
      lastAccessRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      respond(exchange, 200, "{\"id\":9,\"status\":\"PENDING\"}");
    } else {
      respond(exchange, 200, "[{\"id\":1,\"userId\":\"u1\",\"status\":\"PENDING\"}]");
    }
  }

  private static String queryParam(HttpExchange exchange, String name) {
    String query = exchange.getRequestURI().getRawQuery();
    if (query == null) return null;
    for (String pair : query.split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0 && pair.substring(0, equals).equals(name)) {
        return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
