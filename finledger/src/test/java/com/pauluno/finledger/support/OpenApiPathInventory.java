package com.pauluno.finledger.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Extracts a stable {@code path + method + operationId} inventory from OpenAPI 3 JSON
 * for FL-160 contract drift detection.
 */
public final class OpenApiPathInventory {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private OpenApiPathInventory() {
    }

    public record Entry(String path, String method, String operationId) {
        public String key() {
            return method.toUpperCase() + " " + path;
        }
    }

    public static List<Entry> extract(JsonNode openApiRoot) {
        List<Entry> entries = new ArrayList<>();
        JsonNode paths = openApiRoot.path("paths");
        if (!paths.isObject()) {
            return List.of();
        }
        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathFields.next();
            String path = pathEntry.getKey();
            JsonNode methods = pathEntry.getValue();
            if (!methods.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> methodFields = methods.fields();
            while (methodFields.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodFields.next();
                String method = methodEntry.getKey().toLowerCase();
                if (!isHttpMethod(method)) {
                    continue;
                }
                JsonNode op = methodEntry.getValue();
                String operationId = op.path("operationId").asText("");
                entries.add(new Entry(path, method.toUpperCase(), operationId));
            }
        }
        entries.sort(Comparator
                .comparing(Entry::path)
                .thenComparing(Entry::method)
                .thenComparing(Entry::operationId));
        return entries;
    }

    public static String toSnapshotJson(List<Entry> entries) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("description", "FL-160 OpenAPI path/operation inventory — regenerate intentionally when API changes");
        ArrayNode arr = root.putArray("operations");
        for (Entry e : entries) {
            ObjectNode row = arr.addObject();
            row.put("path", e.path());
            row.put("method", e.method());
            row.put("operationId", e.operationId());
        }
        return MAPPER.writeValueAsString(root) + "\n";
    }

    public static List<Entry> fromSnapshotJson(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        List<Entry> entries = new ArrayList<>();
        for (JsonNode n : root.path("operations")) {
            entries.add(new Entry(
                    n.path("path").asText(),
                    n.path("method").asText().toUpperCase(),
                    n.path("operationId").asText("")));
        }
        entries.sort(Comparator
                .comparing(Entry::path)
                .thenComparing(Entry::method)
                .thenComparing(Entry::operationId));
        return entries;
    }

    public static String driftMessage(List<Entry> expected, List<Entry> actual) {
        Map<String, Entry> exp = new TreeMap<>();
        Map<String, Entry> act = new TreeMap<>();
        expected.forEach(e -> exp.put(e.key(), e));
        actual.forEach(e -> act.put(e.key(), e));

        StringBuilder sb = new StringBuilder();
        sb.append("OpenAPI contract drift detected. Update docs/contracts/openapi-paths.json intentionally.\n");
        sb.append("Regenerate: ./mvnw -pl finledger -Dtest=ApiContractIntegrationTest");
        sb.append(" -Dfinledger.contracts.write=true test\n");

        for (String key : exp.keySet()) {
            if (!act.containsKey(key)) {
                sb.append("  REMOVED ").append(key)
                        .append(" operationId=").append(exp.get(key).operationId()).append('\n');
            } else if (!Objects.equals(exp.get(key).operationId(), act.get(key).operationId())) {
                sb.append("  RENAMED operationId ").append(key)
                        .append(" ").append(exp.get(key).operationId())
                        .append(" -> ").append(act.get(key).operationId()).append('\n');
            }
        }
        for (String key : act.keySet()) {
            if (!exp.containsKey(key)) {
                sb.append("  ADDED ").append(key)
                        .append(" operationId=").append(act.get(key).operationId()).append('\n');
            }
        }
        return sb.toString();
    }

    public static boolean equalInventories(List<Entry> expected, List<Entry> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            Entry e = expected.get(i);
            Entry a = actual.get(i);
            if (!e.path().equals(a.path())
                    || !e.method().equals(a.method())
                    || !e.operationId().equals(a.operationId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "patch", "delete", "head", "options", "trace" -> true;
            default -> false;
        };
    }
}
