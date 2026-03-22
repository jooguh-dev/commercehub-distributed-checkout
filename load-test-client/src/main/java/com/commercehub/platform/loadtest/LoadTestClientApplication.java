package com.commercehub.platform.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public final class LoadTestClientApplication {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LoadTestClientApplication() {
    }

    public static void main(String[] args) throws Exception {
        LoadTestOptions options = LoadTestOptions.fromArgs(args);
        LoadTestMetrics metrics = execute(options);
        printSummary(options, metrics);
    }

    static LoadTestMetrics execute(LoadTestOptions options) throws InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        LongAdder successfulRequests = new LongAdder();
        LongAdder unsuccessfulRequests = new LongAdder();
        LongAdder createCartTimeMillis = new LongAdder();
        LongAdder addItemTimeMillis = new LongAdder();
        LongAdder checkoutTimeMillis = new LongAdder();
        Map<Integer, LongAdder> statusCounts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(options.requestCount());
        ExecutorService executorService = Executors.newFixedThreadPool(options.threadCount());

        long startedAt = System.nanoTime();
        for (int i = 0; i < options.requestCount(); i++) {
            int customerId = options.customerIdStart() + i;
            executorService.submit(() -> {
                try {
                    ScenarioResult scenarioResult = executeScenario(client, options, customerId);
                    int statusCode = scenarioResult.statusCode();
                    statusCounts.computeIfAbsent(statusCode, ignored -> new LongAdder()).increment();
                    if (statusCode == 200) {
                        successfulRequests.increment();
                        createCartTimeMillis.add(scenarioResult.createCartTimeMillis());
                        addItemTimeMillis.add(scenarioResult.addItemTimeMillis());
                        checkoutTimeMillis.add(scenarioResult.checkoutTimeMillis());
                    } else {
                        unsuccessfulRequests.increment();
                    }
                } catch (Exception exception) {
                    statusCounts.computeIfAbsent(-1, ignored -> new LongAdder()).increment();
                    unsuccessfulRequests.increment();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);
        long wallTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        LoadTestMetrics metrics = new LoadTestMetrics(
                successfulRequests.sum(),
                unsuccessfulRequests.sum(),
                wallTimeMillis,
                createCartTimeMillis.sum(),
                addItemTimeMillis.sum(),
                checkoutTimeMillis.sum()
        );
        options.setStatusCounts(statusCounts);
        return metrics;
    }

    private static ScenarioResult executeScenario(HttpClient client, LoadTestOptions options, int customerId)
            throws IOException, InterruptedException {
        long createCartStartedAt = System.nanoTime();
        String shoppingCartId = createCart(client, options, customerId);
        long createCartTimeMillis = elapsedMillis(createCartStartedAt);

        long addItemStartedAt = System.nanoTime();
        addItem(client, options, shoppingCartId);
        long addItemTimeMillis = elapsedMillis(addItemStartedAt);

        long checkoutStartedAt = System.nanoTime();
        int statusCode = checkout(client, options, shoppingCartId);
        long checkoutTimeMillis = elapsedMillis(checkoutStartedAt);

        return new ScenarioResult(statusCode, createCartTimeMillis, addItemTimeMillis, checkoutTimeMillis);
    }

    private static String createCart(HttpClient client, LoadTestOptions options, int customerId)
            throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(options.baseUrl() + "/shopping-cart")
                .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":" + customerId + "}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ensureStatus(response, 201, "create cart");
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        return body.get("shoppingCartId").asText();
    }

    private static void addItem(HttpClient client, LoadTestOptions options, String shoppingCartId)
            throws IOException, InterruptedException {
        String payload = "{\"productId\":" + options.productId() + ",\"quantity\":" + options.quantity() + "}";
        HttpRequest request = requestBuilder(options.baseUrl() + "/shopping-carts/" + shoppingCartId + "/addItem")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ensureStatus(response, 204, "add item");
    }

    private static int checkout(HttpClient client, LoadTestOptions options, String shoppingCartId)
            throws IOException, InterruptedException {
        String payload = "{\"creditCardNumber\":\"" + options.creditCardNumber() + "\"}";
        HttpRequest request = requestBuilder(options.baseUrl() + "/shopping-carts/" + shoppingCartId + "/checkout")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private static HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
    }

    private static void ensureStatus(HttpResponse<String> response, int expectedStatus, String operation) {
        if (response.statusCode() != expectedStatus) {
            throw new IllegalStateException(operation + " failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    private static void printSummary(LoadTestOptions options, LoadTestMetrics metrics) {
        System.out.println("Load test completed");
        System.out.println("Base URL: " + options.baseUrl());
        System.out.println("Requests: " + metrics.totalRequests());
        System.out.println("Threads: " + options.threadCount());
        System.out.println("Successes: " + metrics.successfulRequests());
        System.out.println("Failures: " + metrics.unsuccessfulRequests());
        System.out.printf("Success rate: %.2f%%%n", metrics.successRate() * 100.0);
        System.out.printf("Throughput: %.2f req/s%n", metrics.throughputPerSecond());
        System.out.println("Wall time (ms): " + metrics.wallTimeMillis());
        if (metrics.successfulRequests() > 0) {
            System.out.printf("Avg create cart (ms): %.2f%n", metrics.averageCreateCartMillis());
            System.out.printf("Avg add item (ms): %.2f%n", metrics.averageAddItemMillis());
            System.out.printf("Avg checkout (ms): %.2f%n", metrics.averageCheckoutMillis());
            System.out.printf("Avg successful scenario (ms): %.2f%n", metrics.successfulScenarioAverageMillis());
        }
        options.statusCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("Status " + entry.getKey() + ": " + entry.getValue()));
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    static final class LoadTestOptions {
        private final String baseUrl;
        private final int requestCount;
        private final int threadCount;
        private final int customerIdStart;
        private final int productId;
        private final int quantity;
        private final String creditCardNumber;
        private Map<Integer, Long> statusCounts = Map.of();

        private LoadTestOptions(String baseUrl,
                                int requestCount,
                                int threadCount,
                                int customerIdStart,
                                int productId,
                                int quantity,
                                String creditCardNumber) {
            this.baseUrl = baseUrl;
            this.requestCount = requestCount;
            this.threadCount = threadCount;
            this.customerIdStart = customerIdStart;
            this.productId = productId;
            this.quantity = quantity;
            this.creditCardNumber = creditCardNumber;
        }

        static LoadTestOptions fromArgs(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("Arguments must use --key=value format");
                }
                int separator = arg.indexOf('=');
                values.put(arg.substring(2, separator), arg.substring(separator + 1));
            }

            return new LoadTestOptions(
                    values.getOrDefault("base-url", "http://localhost:18080"),
                    parsePositiveInt(values.getOrDefault("requests", "100"), "requests"),
                    parsePositiveInt(values.getOrDefault("threads", "8"), "threads"),
                    parsePositiveInt(values.getOrDefault("customer-id-start", "1000"), "customer-id-start"),
                    parsePositiveInt(values.getOrDefault("product-id", "1"), "product-id"),
                    parsePositiveInt(values.getOrDefault("quantity", "1"), "quantity"),
                    values.getOrDefault("credit-card-number", "1234-5678-9012-3456")
            );
        }

        private static int parsePositiveInt(String value, String name) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return parsed;
        }

        String baseUrl() {
            return baseUrl;
        }

        int requestCount() {
            return requestCount;
        }

        int threadCount() {
            return threadCount;
        }

        int customerIdStart() {
            return customerIdStart;
        }

        int productId() {
            return productId;
        }

        int quantity() {
            return quantity;
        }

        String creditCardNumber() {
            return creditCardNumber;
        }

        Map<Integer, Long> statusCounts() {
            return statusCounts;
        }

        void setStatusCounts(Map<Integer, LongAdder> counters) {
            Map<Integer, Long> snapshot = new LinkedHashMap<>();
            counters.forEach((statusCode, count) -> snapshot.put(statusCode, count.sum()));
            statusCounts = snapshot;
        }
    }
}
