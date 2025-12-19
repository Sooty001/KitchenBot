package com.example.kitchenbot.agent.tool;

import com.example.kitchenbot.bot.KitchenBot;
import com.example.kitchenbot.service.TimerService;
import com.example.kitchenbot.util.FileUtil;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class KitchenTools {

    private final TimerService timerService;

    public KitchenTools(TimerService timerService) {
        this.timerService = timerService;
    }

    @Tool("Сохраняет сгенерированный список покупок в файл")
    public File createShoppingListFile(String content) {
        return FileUtil.saveShoppingList(content);
    }

    @Tool("Запускает таймер обратного отсчета для пользователя в чате")
    public void startUserTimer(KitchenBot bot, long chatId, int messageId, long seconds) {
        timerService.startTimer(bot, chatId, messageId, seconds);
    }
}
