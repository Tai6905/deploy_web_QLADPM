import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Run from the repo root. Starts the production JAR with environment-only DB settings, then stops it. */
class ProductionStartupCheck {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of("backend/target/catalog-api-0.1.0-SNAPSHOT.jar").toAbsolutePath();
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Package the backend JAR first.");
        var settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("backend/application-local.properties"))) { settings.load(reader); }
        String password = settings.getProperty("spring.datasource.password", "${DB_PASSWORD:}");
        if (password.startsWith("${")) password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Set the private DB password first.");
        int port;
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        Path log = Path.of("backend/target/production-startup-check.log").toAbsolutePath();
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var builder = new ProcessBuilder(executable, "-jar", jar.toString(),
                "--spring.profiles.active=production", "--spring.config.import=",
                "--app.bootstrap.owner.email=", "--app.bootstrap.warehouse.email=");
        var environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("SPRING_") || key.startsWith("BOOTSTRAP_")
                || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS") || key.equals("SERVER_PORT"));
        environment.put("PORT", Integer.toString(port));
        environment.put("DB_URL", settings.getProperty("spring.datasource.url"));
        environment.put("DB_USERNAME", settings.getProperty("spring.datasource.username"));
        environment.put("DB_PASSWORD", password);
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process child = builder.start();
        boolean ready = false;
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/session"))
                    .timeout(Duration.ofSeconds(2)).build();
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (child.isAlive() && System.nanoTime() < deadline) {
                try {
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && response.body().contains("\"authenticated\":false")) { ready = true; break; }
                } catch (java.io.IOException ignored) { }
                Thread.sleep(1000);
            }
            if (!ready) {
                String output = Files.readString(log).replace(password, "[REDACTED]");
                output.lines().skip(Math.max(0, output.lines().count() - 45)).forEach(System.err::println);
                throw new IllegalStateException("Production startup check failed; inspect the sanitized log above.");
            }
            System.out.println("PASS: production JAR starts with DB environment variables, reads PORT, and serves the Railway healthcheck.");
            System.out.println("No local properties were imported by the server; no migrations or business writes were requested.");
            System.out.println("Runtime tested: Java " + System.getProperty("java.version") + ". Railway logs are still needed to diagnose its crash.");
        } finally {
            child.destroy();
            if (!child.waitFor(10, TimeUnit.SECONDS)) child.destroyForcibly();
        }
    }
}
