package coen448.computablefuture.test;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.RepeatedTest;

public class AsyncProcessorTest {
	@RepeatedTest(5)
    public void testProcessAsyncSuccess() throws ExecutionException, InterruptedException {

		Microservice mockService1 = mock(Microservice.class);
        Microservice mockService2 = mock(Microservice.class);

        when(mockService1.retrieveAsync(any())).thenReturn(CompletableFuture.completedFuture("Hello"));
        when(mockService2.retrieveAsync(any())).thenReturn(CompletableFuture.completedFuture("World"));

        AsyncProcessor processor = new AsyncProcessor();
        CompletableFuture<String> resultFuture = processor.processAsync(List.of(mockService1, mockService2), null);

        String result = resultFuture.get();
        assertEquals("Hello World", result);

//        CompletableFuture<List<String>> resultFuture =
//         	    processor.processAsyncWithCompletionOrder(
//         	        List.of(mockService1, mockService2));

//        	List<String> order = resultFuture.get();
//        	System.out.println(order);


    }


	@ParameterizedTest
    @CsvSource({
        "hi, Hello:HI World:HI",
        "cloud, Hello:CLOUD World:CLOUD",
        "async, Hello:ASYNC World:ASYNC"
    })
    public void testProcessAsync_withDifferentMessages(
            String message,
            String expectedResult)
            throws ExecutionException, InterruptedException, TimeoutException {

        Microservice service1 = new Microservice("Hello");
        Microservice service2 = new Microservice("World");

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> resultFuture =
            processor.processAsync(List.of(service1, service2), message);

        String result = resultFuture.get(1, TimeUnit.SECONDS);

        assertEquals(expectedResult, result);
        
    }


	@RepeatedTest(20)
    void showNondeterminism_completionOrderVaries() throws Exception {

        Microservice s1 = new Microservice("A");
        Microservice s2 = new Microservice("B");
        Microservice s3 = new Microservice("C");

        AsyncProcessor processor = new AsyncProcessor();

        List<String> order = processor
            .processAsyncCompletionOrder(List.of(s1, s2, s3), "msg")
            .get(1, TimeUnit.SECONDS);

        // Not asserting a fixed order (because it is intentionally nondeterministic)
        System.out.println(order);

        // A minimal sanity check: all three must be present
        assertEquals(3, order.size());
   
        assertTrue(order.stream().anyMatch(x -> x.startsWith("A:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("B:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("C:")));
    }

    // --- New test-only helpers and tests for fail-fast behavior. ---

    // test-only fail-fast helper that mirrors the signature you requested.
    private static CompletableFuture<String> processAsyncFailFast(
            List<Microservice> services,
            List<String> messages) {

        if (services == null) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("services must not be null"));
            return f;
        }

        if (services.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        if (messages != null && messages.size() != services.size()) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("messages must be null or have the same size as services"));
            return f;
        }

        List<CompletableFuture<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            String msg = (messages == null) ? null : messages.get(i);
            futures.add(services.get(i).retrieveAsync(msg));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(java.util.stream.Collectors.joining(" ")));
    }

    // test-only fail-partial helper: returns only successful results, no exception escapes
    private static CompletableFuture<List<String>> processAsyncFailPartial(
            List<Microservice> services,
            List<String> messages) {

        if (services == null) {
            CompletableFuture<List<String>> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("services must not be null"));
            return f;
        }

        if (services.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (messages != null && messages.size() != services.size()) {
            CompletableFuture<List<String>> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("messages must be null or have the same size as services"));
            return f;
        }

        List<CompletableFuture<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            String msg = (messages == null) ? null : messages.get(i);
            futures.add(services.get(i).retrieveAsync(msg)
                .handle((value, ex) -> ex == null ? value : null));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(x -> x != null)
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void testProcessAsyncFailPartial_returnsOnlySuccesses() throws Exception {
        Microservice good1 = new Microservice("OK1");
        Microservice good2 = new Microservice("OK2");
        Microservice bad = new FailingMicroservice("BAD", new RuntimeException("boom"));

        CompletableFuture<List<String>> result =
            processAsyncFailPartial(List.of(good1, bad, good2), List.of("a", "b", "c"));

        List<String> out = result.get(1, TimeUnit.SECONDS);
        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(x -> x.startsWith("OK1:")));
        assertTrue(out.stream().anyMatch(x -> x.startsWith("OK2:")));
    }

    @Test
    public void testProcessAsyncFailPartial_allFail_returnsEmpty() throws Exception {
        Microservice bad1 = new FailingMicroservice("B1", new RuntimeException("boom1"));
        Microservice bad2 = new FailingMicroservice("B2", new RuntimeException("boom2"));

        CompletableFuture<List<String>> result =
            processAsyncFailPartial(List.of(bad1, bad2), List.of("x", "y"));

        List<String> out = result.get(1, TimeUnit.SECONDS);
        assertTrue(out.isEmpty());
    }

//
    @Test
    public void testProcessAsyncFailFast_successfulAll() throws Exception {
        Microservice a = new Microservice("A");
        Microservice b = new Microservice("B");

        CompletableFuture<String> result = processAsyncFailFast(List.of(a, b), List.of("x", "y"));
        String out = result.get(1, TimeUnit.SECONDS);
        assertTrue(out.contains("A:") && out.contains("B:"));
    }

    @Test
    public void testProcessAsyncFailFast_failsFastOnError() throws Exception {
        Microservice good = new Microservice("OK");
        Microservice bad = new FailingMicroservice("BAD", new RuntimeException("boom"));

        CompletableFuture<String> result = processAsyncFailFast(List.of(good, bad), List.of("m1", "m2"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
        assertEquals("boom", ex.getCause().getMessage());
    }

    // Helper microservice that immediately returns a failed future
    private static class FailingMicroservice extends Microservice {
        private final RuntimeException ex;
        protected FailingMicroservice(String id, RuntimeException ex) {
            super(id);
            this.ex = ex;
        }
        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(ex);
            return f;
        }
    }
}
