package com.example.kitchenbot.service;

import com.example.kitchenbot.agent.ChefAgent;
import com.example.kitchenbot.agent.ShoppingAgent;
import com.example.kitchenbot.model.AgentResponse;
import com.example.kitchenbot.model.SearchMode;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final ChefAgent chefAgent;
    private final ShoppingAgent shoppingAgent;

    public AgentOrchestrator(ChefAgent chefAgent, ShoppingAgent shoppingAgent) {
        this.chefAgent = chefAgent;
        this.shoppingAgent = shoppingAgent;
    }

    public AgentResponse processRequest(String userText, String history, SearchMode mode) {
        String chefResponse = chefAgent.process(userText, history, mode);
        String finalResponse = chefResponse;
        String filePath = null;

        String lower = chefResponse.toLowerCase();
        boolean isRecipe = (lower.contains("ингредиенты") || lower.contains("понадобится"))
                && (lower.contains("инструкция") || lower.contains("приготовление"));

        if (isRecipe) {
            String shoppingResult = shoppingAgent.process(chefResponse, history);

            if (shoppingResult.contains("EMPTY_LIST")) {
                finalResponse += "\n\n✅ **Менеджер:** У вас уже есть все продукты!";
            } else {
                if (shoppingResult.contains("[[FILE_PATH:")) {
                    int start = shoppingResult.indexOf("[[FILE_PATH:") + 12;
                    int end = shoppingResult.indexOf("]]", start);
                    filePath = shoppingResult.substring(start, end);
                    shoppingResult = shoppingResult.substring(0, start - 12).trim();
                }
                finalResponse += "\n\n📜 Составил список недостающих продуктов (см. файл).";
            }
        }
        return new AgentResponse(finalResponse, filePath);
    }
}