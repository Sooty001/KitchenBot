package com.example.kitchenbot.agent;

import com.example.kitchenbot.model.SearchMode;
import com.example.kitchenbot.service.KnowledgeBaseService;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChefAgent {

    private final Client client;
    private final KnowledgeBaseService ragService;

    @Value("${gemini.model.name}")
    private String modelName;

    public ChefAgent(Client client, KnowledgeBaseService ragService) {
        this.client = client;
        this.ragService = ragService;
    }

    public String process(String input, String history, SearchMode mode) {
        String context = ragService.findRelevantContext(input);
        if (context.isEmpty()) context = "В книгах рецептов ничего не найдено.";

        String strictInstruction = (mode == SearchMode.STRICT_RAG)
                ? "Используй ТОЛЬКО контекст. Если нет ответа, скажи 'Не знаю'."
                : "Используй контекст и свои знания.";

        String prompt = """
            Ты - Умный Кухонный Ассистент.

            ПРАВИЛА ВЫВОДА (СОБЛЮДАЙ СТРОГО):
            1. НИКОГДА не пиши название сценария (например, "СЦЕНАРИЙ 2"). Сразу начинай ответ.
            2. НЕ ПРЕДСТАВЛЯЙСЯ.

            ТВОЯ ЛОГИКА:

            СЦЕНАРИЙ 1: Пользователь просит ТАЙМЕР (например: "засеки 10 мин", "таймер 5 минут").
            - Просто ответь: "Хорошо, таймер установлен."
                - В конце добавь тег [[TIMER:...]] по правилам:
                  * Если время в формате ЧЧ:ММ (например "10:36"), считай это ЧАСАМИ: [[TIMER:10 ч 36 мин]].
                  * Если время словами ("15 минут"), сохраняй единицы: [[TIMER:15 мин]].
                  * НИКОГДА не пиши в тег просто "15:15" без слов (это вызывает ошибки).

            СЦЕНАРИЙ 2: Пользователь спрашивает РЕЦЕПТ или про ЕДУ.
            - %s
            - Структура ответа:
              * Название блюда
              * Ингредиенты (слово "Ингредиенты" обязательно)
              * Инструкция
            - Если в рецепте есть этапы ожидания (пример: варить 10-15 минут), добавь [[TIMER:секунды]] в конце (добавляй этот тег столько раз сколько видишь временных интервалов в рецепте, добавляй среднее значение времени).

            Обычный разговор ("привет", "кто ты", "посоветуй что приготовить")
            - Отвечай кратко и вежливо.
            - Давай рекомендации или советы списком если требуется.

            КОНТЕКСТ (RAG):
            %s

            ИСТОРИЯ:
            %s
            """.formatted(strictInstruction, context, history);

        try {
            GenerateContentResponse response = client.models.generateContent(
                    modelName, prompt + "\nЗАПРОС: " + input, null);
            return response.text();
        } catch (Exception e) {
            return "Ошибка AI: " + e.getMessage();
        }
    }
}