package com.example.kitchenbot.service;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private final Client client;
    private final List<ChunkData> vectorStore = new ArrayList<>();
    private static final String EMBEDDING_MODEL = "text-embedding-004";

    public KnowledgeBaseService(Client client) {
        this.client = client;
    }

    public void addDocument(File file) {
        try {
            vectorStore.clear();
            String fullText;
            try {
                fullText = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Document document = new ApacheTikaDocumentParser().parse(new FileInputStream(file));
                fullText = document.text();
            }

            List<String> segments = splitIntoChunks(fullText, 800, 200);
            for (int i = 0; i < segments.size(); i++) {
                String segment = segments.get(i);
                float[] vector = getEmbedding(segment);
                if (vector.length > 0) {
                    vectorStore.add(new ChunkData(i, segment, vector));
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String findRelevantContext(String userQuery) {
        if (vectorStore.isEmpty()) return "";
        float[] queryVector = getEmbedding(userQuery);
        if (queryVector.length == 0) return "";

        List<ScoredChunk> topMatches = vectorStore.stream()
                .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(chunk.vector, queryVector)))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(3)
                .collect(Collectors.toList());

        Set<Integer> indicesToKeep = new HashSet<>();
        int neighborRadius = 2;

        for (ScoredChunk match : topMatches) {
            if (match.score < 0.40) continue;
            int id = match.chunk.id;
            indicesToKeep.add(id);
            for (int i = 1; i <= neighborRadius; i++) {
                if (id - i >= 0) indicesToKeep.add(id - i);
                if (id + i < vectorStore.size()) indicesToKeep.add(id + i);
            }
        }

        if (indicesToKeep.isEmpty()) return "";

        return indicesToKeep.stream()
                .sorted()
                .map(index -> vectorStore.get(index).text)
                .collect(Collectors.joining("\n ... \n"));
    }

    private float[] getEmbedding(String text) {
        try {
            EmbedContentResponse response = client.models.embedContent(EMBEDDING_MODEL, text, null);
            if (response.embeddings().isPresent() && !response.embeddings().get().isEmpty()) {
                List<Float> values = response.embeddings().get().get(0).values().orElse(new ArrayList<>());
                float[] result = new float[values.size()];
                for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
                return result;
            }
        } catch (Exception e) {
            System.err.println("Ошибка API: " + e.getMessage());
        }
        return new float[0];
    }

    private List<String> splitIntoChunks(String text, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int lastSpace = text.substring(start, end).lastIndexOf(' ');
                if (lastSpace > 0) end = start + lastSpace;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end == text.length()) break;
            start = end - overlap;
            if (start < 0) start = 0;
            if (start >= end) start = end;
        }
        return chunks;
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        if (vectorA.length != vectorB.length) return 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ChunkData(int id, String text, float[] vector) {}
    private record ScoredChunk(ChunkData chunk, double score) {}
}