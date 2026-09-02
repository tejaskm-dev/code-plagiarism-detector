package com.integrityengine.ai;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * A stand-in for {@link HttpClient} that records what it was asked and returns a canned
 * reply, so the transport layer can be tested without a socket.
 *
 * <p>This exists because a mutation survived without it: removing the blank-key guard
 * still produced an empty result (the real call simply failed), so the test could not
 * tell "refused to call" from "called and failed". Counting sends distinguishes them.
 */
final class FakeHttpClient extends HttpClient {

    int sendCount;
    HttpRequest lastRequest;

    private final int status;
    private final String body;
    private final Exception failure;

    private FakeHttpClient(int status, String body, Exception failure) {
        this.status = status;
        this.body = body;
        this.failure = failure;
    }

    static FakeHttpClient replying(int status, String body) {
        return new FakeHttpClient(status, body, null);
    }

    static FakeHttpClient failingWith(Exception failure) {
        return new FakeHttpClient(0, "", failure);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        sendCount++;
        lastRequest = request;
        if (failure instanceof IOException io) {
            throw io;
        }
        if (failure instanceof InterruptedException interrupted) {
            throw interrupted;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        return (HttpResponse<T>) new FakeResponse(status, body, request);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SSLParameters sslParameters() {
        return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }

    private static final class FakeResponse implements HttpResponse<String> {
        private final int status;
        private final String body;
        private final HttpRequest request;

        FakeResponse(int status, String body, HttpRequest request) {
            this.status = status;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }

    List<String> headerValues(String name) {
        return lastRequest == null ? List.of() : lastRequest.headers().allValues(name);
    }
}
