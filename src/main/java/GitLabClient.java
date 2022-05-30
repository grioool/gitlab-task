import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GitLabClient {

    private final OkHttpClient httpClient = new OkHttpClient();

    private final String baseUrl;

    private final String token;

    public GitLabClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public List<String> getPaths(Integer groupId) throws IOException {
        return
            getItems(groupId).stream()
                .map(item -> String.format("%d: %s", (Integer) item.get("id"), item.get("path")))
                .collect(Collectors.toList());
    }

    public List<Map<String, ?>> getItems(Integer groupId) throws IOException {
        return
            concatAll(List.of(
                Stream.of(getGroup(groupId)),
                getSubgroups(groupId).stream()
                    .flatMap(subgroup -> {
                        try {
                            return getItems((Integer) subgroup.get("id")).stream();
                        } catch (IOException e) {
                            handleException(e);
                            return Stream.empty();
                        }
                    }),
                getProjects(groupId).stream(),
                getRepositories(groupId).stream()
            ))
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, ?> getGroup(Integer groupId) throws IOException {
        return call(String.format("/groups/%d", groupId), new TypeReference<>() {
        });
    }

    private List<Map<String, ?>> getSubgroups(Integer groupId) {
        try {
            return call(String.format("/groups/%d/subgroups", groupId), new TypeReference<>() {
            });
        } catch (IOException e) {
            handleException(e);
            return List.of();
        }
    }

    private List<Map<String, ?>> getProjects(Integer groupId) {
        try {
            return call(String.format("/groups/%d/projects", groupId), new TypeReference<>() {
            });
        } catch (IOException e) {
            handleException(e);
            return List.of();
        }
    }

    private List<Map<String, ?>> getRepositories(Integer groupId) {
        try {
            return call(String.format("/groups/%d/registry/repositories/", groupId), new TypeReference<>() {
            });
        } catch (IOException e) {
            handleException(e);
            return List.of();
        }
    }

    private <R> R call(String path, TypeReference<R> typeReference) throws IOException {
        try (Response response = httpClient.newCall(buildRequest(path)).execute()) {
            if (response.code() != 200) {
                ERROR_CODE_HANDLERS.get(response.code()).handle(response);
                return null;
            }
            if (response.body() == null) throw new IOException("Body not found");

            ResponseBody responseBody = response.body();

            return new ObjectMapper().readValue(
                responseBody.string(),
                typeReference
            );
        }
    }

    private Request buildRequest(String path) {
        return new Request.Builder()
            .url(baseUrl + path)
            .header("User-Agent", "OkHttp Headers.java")
            .addHeader("Authorization", String.format("Bearer %s", token))
            .build();
    }

    private void handleException(Exception e) {
        System.out.println("Error: " + e.getMessage());
    }

    private static final Map<Integer, ResponseHandler> ERROR_CODE_HANDLERS =
        Map.of(
            404, (response) -> {
                throw new IOException("Not found");
            },
            401, (response) -> {
                throw new IOException("Not authorized");
            }
        );

    @FunctionalInterface
    private interface ResponseHandler {
        void handle(Response response) throws IOException;
    }

    private static <T> Stream<T> concatAll(List<Stream<T>> streams) {
        return streams.stream().reduce(Stream.empty(), Stream::concat);
    }
}
