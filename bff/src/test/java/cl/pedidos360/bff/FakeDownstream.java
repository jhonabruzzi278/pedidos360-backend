package cl.pedidos360.bff;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Microservicio falso sobre HTTP real, para probar el BFF como proxy sin levantar los servicios. */
final class FakeDownstream implements AutoCloseable {
  private static final String CREATED_BODY = "{\"id\":\"OT-NEW\"}";

  private final HttpServer server;
  private final AtomicReference<String> lastCreateBody = new AtomicReference<>();
  private final AtomicReference<String> lastCreateContentType = new AtomicReference<>();
  private final AtomicInteger createCalls = new AtomicInteger();
  private volatile int createStatus = 201;
  private volatile String createBody = CREATED_BODY;
  private volatile int eventsStatus = 200;
  private volatile Duration eventsDelay = Duration.ZERO;

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

  /** Devuelve el servicio a su comportamiento normal; llamar antes de cada prueba. */
  void reset() {
    createStatus = 201;
    createBody = CREATED_BODY;
    eventsStatus = 200;
    eventsDelay = Duration.ZERO;
    createCalls.set(0);
    lastCreateBody.set(null);
    lastCreateContentType.set(null);
  }

  private void workOrders(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      respond(exchange, 200, "[{\"id\":\"OT-1\"}]");
      return;
    }
    createCalls.incrementAndGet();
    lastCreateBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    lastCreateContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
    respond(exchange, createStatus, createBody);
  }

  private void events(HttpExchange exchange) throws IOException {
    sleep(eventsDelay);
    respond(exchange, eventsStatus, eventsStatus == 200
        ? "[{\"id\":1,\"eventType\":\"OtCreada\"}]"
        : "{\"trace\":\"java.lang.NullPointerException\"}");
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
