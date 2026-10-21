package com.modernshopflow.catalog.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/** Shared option vocabulary is packaged from src/catalog/instruments.json. */
public final class InstrumentSpecifications {
    public record Option(String value, String label) {}
    public record Field(String key, String label, List<Option> options) {}
    public record Type(String value, String label, List<Field> fields) {}
    public record Schema(List<Type> types) {}
    private static final Schema SCHEMA = load();

    private static Schema load() {
        try (var input = new ClassPathResource("catalog/instruments.json").getInputStream()) {
            return new ObjectMapper().readValue(input, Schema.class);
        } catch (Exception error) { throw new IllegalStateException("Cannot load instrument specifications", error); }
    }

    public static Map<String, String> validate(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        if (input.size() > 5 || input.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null))
            throw new IllegalArgumentException("Thông số nhạc cụ không hợp lệ.");
        var type = SCHEMA.types().stream().filter(t -> t.value().equals(input.get("instrumentType")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Hãy chọn loại nhạc cụ hợp lệ trước khi chọn thông số."));
        var clean = new LinkedHashMap<String, String>();
        clean.put("instrumentType", type.value());
        for (var entry : input.entrySet()) {
            if (entry.getKey().equals("instrumentType")) continue;
            var field = type.fields().stream().filter(f -> f.key().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Thông số không thuộc loại nhạc cụ đã chọn: " + entry.getKey()));
            if (field.options().stream().noneMatch(option -> option.value().equals(entry.getValue())))
                throw new IllegalArgumentException("Giá trị không hợp lệ cho " + field.label());
            clean.put(entry.getKey(), entry.getValue());
        }
        return clean;
    }

    private InstrumentSpecifications() {}
}
