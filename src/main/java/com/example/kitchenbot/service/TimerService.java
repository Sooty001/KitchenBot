package com.example.kitchenbot.service;

import com.example.kitchenbot.util.TextUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.bots.AbsSender;

@Service
public class TimerService {

    @Async
    public void startTimer(AbsSender bot, long chatId, int messageId, long seconds) {
        long left = seconds;
        while (left > 0) {
            try {
                Thread.sleep(5000);
                left -= 5;
                if (left < 0) left = 0;

                EditMessageText edit = new EditMessageText();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(messageId);
                edit.setText("⏳ Осталось: " + TextUtil.formatDuration(left));

                bot.execute(edit);
            } catch (Exception e) {
                return;
            }
        }

        try {
            EditMessageText finalEdit = new EditMessageText();
            finalEdit.setChatId(String.valueOf(chatId));
            finalEdit.setMessageId(messageId);
            finalEdit.setText("🏁 <b>Таймер завершен!</b>");
            finalEdit.setParseMode("HTML");
            bot.execute(finalEdit);

            SendMessage alert = new SendMessage();
            alert.setChatId(String.valueOf(chatId));
            alert.setText("⏰ <b>ДЗЫНЬ-ДЗЫНЬ! Время вышло!</b> (" + TextUtil.formatDuration(seconds) + ")");
            alert.setParseMode("HTML");
            bot.execute(alert);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}