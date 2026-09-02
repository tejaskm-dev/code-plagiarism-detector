package com.integrityengine.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal HTTP + multipart helper, so the tests speak the wire format a browser sends. */
final class HttpSupport {

    private static final String BOUNDARY = "----IntegrityEngineTestBoundary";

    private final HttpClient client = HttpClient.newHttpClient();
    private final String base;

    HttpSupport(int port) {
        this.base = "http://localhost:" + port;
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> postForm(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> patchForm(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "application/x-www-form-urlencoded")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> delete(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> postEmpty(String path) throws Exception {
        return postEmpty(path, null, null);
    }

    HttpResponse<String> postEmpty(String path, String headerName, String headerValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Posts files as a browser would: multipart/form-data under the field name "files". */
    HttpResponse<String> postFiles(String path, List<Upload> uploads) throws Exception {
        byte[] body = multipart(uploads);
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Posts a single binary part, used for archive uploads. */
    HttpResponse<String> postZip(String path, String filename, byte[] archive) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"files\"; filename=\""
                + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(archive);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a body that claims to be multipart but is not, to exercise the error path. */
    HttpResponse<String> postBrokenMultipart(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofString("this is not a multipart body at all"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] multipart(List<Upload> uploads) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Upload upload : uploads) {
            out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"files\"; filename=\""
                    + upload.filename() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: text/plain\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(upload.content().getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    static List<Upload> files(String... filenameThenContent) {
        List<Upload> uploads = new ArrayList<>();
        for (int i = 0; i < filenameThenContent.length; i += 2) {
            uploads.add(new Upload(filenameThenContent[i], filenameThenContent[i + 1]));
        }
        return uploads;
    }

    record Upload(String filename, String content) {
    }

    /** Crude field extraction: enough to assert on a response without a JSON library. */
    static String field(String json, String name) {
        int at = json.indexOf("\"" + name + "\":");
        if (at < 0) {
            return null;
        }
        int start = at + name.length() + 3;
        if (start < json.length() && json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && "-0123456789.truefalsn".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return json.substring(start, end);
    }
}
