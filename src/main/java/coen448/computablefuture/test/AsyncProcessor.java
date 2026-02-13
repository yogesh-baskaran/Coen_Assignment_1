package coen448.computablefuture.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AsyncProcessor {
	
    public CompletableFuture<String> processAsync(List<Microservice> microservices, String message) {
    	
        List<CompletableFuture<String>> futures = microservices.stream()
            .map(client -> client.retrieveAsync(message))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.joining(" ")));
        
    }
    
    public CompletableFuture<List<String>> processAsyncCompletionOrder(
            List<Microservice> microservices, String message) {

        List<String> completionOrder =
            Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = microservices.stream()
            .map(ms -> ms.retrieveAsync(message)
                .thenAccept(completionOrder::add))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> completionOrder);
        
    }

    public CompletableFuture<String> processAsyncFailSafe(
            List<Microservice> microservices,
            List<String> messages,
            String fallback) {

        if (microservices == null) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("microservices must not be null"));
            return f;
        }

        if (microservices.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        if (messages != null && messages.size() != microservices.size()) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(
                new IllegalArgumentException("messages must be null or have the same size as microservices"));
            return f;
        }

        String safeFallback = (fallback == null) ? "ERROR" : fallback;
        List<CompletableFuture<String>> futures = new ArrayList<>(microservices.size());
        for (int i = 0; i < microservices.size(); i++) {
            String msg = (messages == null) ? null : messages.get(i);
            CompletableFuture<String> f = microservices.get(i)
                .retrieveAsync(msg)
                .handle((value, ex) -> ex == null ? value : safeFallback);
            futures.add(f);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.joining(" ")));
    }

}
