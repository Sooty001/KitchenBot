package com.example.kitchenbot.agent;

import com.example.kitchenbot.agent.tool.KitchenTools;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;

@Component
public class ShoppingAgent {

    private final Client client;
    private final KitchenTools tools;

    @Value("${gemini.model.name}")
    private String modelName;

    public ShoppingAgent(Client client, KitchenTools tools) {
        this.client = client;
        this.tools = tools;
    }

    public String process(String recipeText, String history) {
        String prompt = """
            Ты - Строгий Ассистент по закупкам.
            
            Твоя задача: Составить список покупок.
            
            ЛОГИКА:
            1. Посмотри в РЕЦЕПТ - это полный список того, что нужно.
            2. Посмотри в ИСТОРИЮ ЧАТА. Ищи ТОЛЬКО явные фразы пользователя: "У меня есть X", "Купил Y", или распознанные фото ("Вижу продукты: ...").
            3. ВЫЧИТАНИЕ: Если продукт из рецепта УЖЕ ЕСТЬ в истории (у пользователя) -> НЕ добавляй его в список покупок.
            4. Если в истории НЕТ упоминаний, что у пользователя есть продукты -> СЧИТАЙ, ЧТО У НЕГО НЕТ НИЧЕГО и копируй все ингредиенты.
            5. Если список покупок получился пуст (пользователь сказал, что у него всё есть), верни ТОЛЬКО слово: EMPTY_LIST
            
            ИСТОРИЯ ЧАТА:
            %s
            
            РЕЦЕПТ:
            %s
            """.formatted(history, recipeText);

        try {
            GenerateContentResponse response = client.models.generateContent(modelName, prompt, null);
            String resultText = response.text().trim();

            if (resultText.contains("EMPTY_LIST")) return "EMPTY_LIST";

            File file = tools.createShoppingListFile(resultText);
            return resultText + (file != null ? "\n\n[[FILE_PATH:" + file.getAbsolutePath() + "]]" : "");
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }
}