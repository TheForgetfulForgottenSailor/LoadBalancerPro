package core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class LaseShadowReplayReader {
    public static final int DEFAULT_MAX_LINE_LENGTH = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final int maxLineLength;

    public LaseShadowReplayReader() {
        this(DEFAULT_MAX_LINE_LENGTH);
    }

    public LaseShadowReplayReader(int maxLineLength) {
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException("maxLineLength must be positive");
        }
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.maxLineLength = maxLineLength;
    }

    public List<LaseShadowReplayRecord> readAll(Path replayFile) {
        List<LaseShadowReplayRecord> records = new ArrayList<>();
        read(replayFile, records::add);
        return List.copyOf(records);
    }

    public void read(Path replayFile, Consumer<LaseShadowReplayRecord> consumer) {
        Objects.requireNonNull(replayFile, "replayFile cannot be null");
        Objects.requireNonNull(consumer, "consumer cannot be null");

        try (BufferedReader reader = Files.newBufferedReader(replayFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() > maxLineLength) {
                    throw new LaseShadowReplayException(
                            "Replay input line " + lineNumber + " exceeds maximum replay line length");
                }
                if (line.isBlank()) {
                    continue;
                }
                consumer.accept(parseLine(line, lineNumber));
            }
        } catch (NoSuchFileException e) {
            throw new LaseShadowReplayException("Replay file not found: " + replayFile.getFileName(), e);
        } catch (IOException e) {
            throw new LaseShadowReplayException("Unable to read replay file: " + replayFile.getFileName(), e);
        }
    }

    public String toJsonLine(LaseShadowReplayRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new LaseShadowReplayException("Unable to serialize replay record", e);
        }
    }

    private LaseShadowReplayRecord parseLine(String line, int lineNumber) {
        try {
            return objectMapper.readValue(line, LaseShadowReplayRecord.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new LaseShadowReplayException(
                    "Invalid replay record at line " + lineNumber + ": " + safeCauseMessage(e), e);
        }
    }

    private static String safeCauseMessage(Exception e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        if (e instanceof JsonProcessingException && !(cursor instanceof IllegalArgumentException)) {
            return "malformed JSON";
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return "record could not be parsed";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
