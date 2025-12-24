package com.example.kitchenbot.bot;

import com.example.kitchenbot.agent.tool.KitchenTools;
import com.example.kitchenbot.model.AgentResponse;
import com.example.kitchenbot.model.SearchMode;
import com.example.kitchenbot.service.*;
import com.example.kitchenbot.util.FileUtil;
import com.example.kitchenbot.util.TextUtil;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KitchenBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}") private String botUsername;
    @Value("${gemini.model.name}") private String MODEL_NAME;

    private final Client genAiClient;
    private final AgentOrchestrator orchestrator;
    private final SaluteSpeechService speechService;
    private final KnowledgeBaseService ragService;
    private final KitchenTools tools;

    private final Map<Long, Boolean> userVoiceResponse = new ConcurrentHashMap<>();
    private final Map<Long, SearchMode> userSearchMode = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> chatHistory = new ConcurrentHashMap<>();

    private static final String BTN_PREFIX_MODE = "🔄 Режим: ";
    private static final String BTN_PREFIX_VOICE = "🎤 Ответ: ";
    private static final String BTN_HELP = "❓ Помощь";

    public KitchenBot(@Value("${telegram.bot.token}") String botToken,
                      Client genAiClient, AgentOrchestrator orchestrator,
                      SaluteSpeechService speechService, KnowledgeBaseService ragService,
                      KitchenTools tools) {
        super(botToken);
        this.genAiClient = genAiClient;
        this.orchestrator = orchestrator;
        this.speechService = speechService;
        this.ragService = ragService;
        this.tools = tools;
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleTimerCallback(update.getCallbackQuery());
            return;
        }
        if (!update.hasMessage()) return;

        Message msg = update.getMessage();
        long chatId = msg.getChatId();

        if (msg.hasText()) handleTextMessage(chatId, msg.getText());
        else if (msg.hasPhoto()) processPhoto(chatId, msg.getPhoto());
        else if (msg.hasVoice()) processVoice(chatId, msg.getVoice());
        else if (msg.hasDocument()) processDocument(chatId, msg.getDocument());
    }

    private void handleTextMessage(long chatId, String text) {
        if (text.equals("/start") || text.equals(BTN_HELP)) {
            String welcomeMsg = """
                🧑‍🍳 <b>Привет! Я твой Умный Су-шеф.</b>
                Я помогу организовать процесс готовки от идеи до подачи на стол.
                
                <b>Вот что я умею:</b>
                
                🥗 <b>Придумывать рецепты</b>
                Напиши <i>"Хочу пасту"</i> или <i>"У меня есть курица и грибы, что приготовить?"</i>.
                
                📸 <b>Видеть продукты</b>
                Пришли <b>фото</b> содержимого холодильника или стола — я распознаю продукты и предложу рецепт из того, что есть.
                
                🛒 <b>Вести список покупок</b>
                Я автоматически проверю, какие продукты у тебя уже есть (из истории чата или фото), и составлю файл со списком того, что нужно докупить.
                
                ⏱ <b>Следить за временем</b>
                Напиши <i>"Засеки 10 минут"</i> или <i>"Таймер на полчаса"</i> — я запущу обратный отсчет.
                
                📚 <b>Учиться по твоим книгам (RAG)</b>
                Пришли мне файл (PDF, TXT) с рецептами. Я добавлю его в базу знаний и смогу искать ответы именно там!
                
                🎤 <b>Голосовое управление</b>
                Я понимаю голосовые сообщения. А если нажмешь кнопку <b>"🔊 Вкл"</b>, то буду отвечать тебе голосом!
                
                👇 <b>Настройки режимов внизу:</b>
                • <b>🧠 Гибрид:</b> Использую интернет + твои книги.
                • <b>🔒 Строгий:</b> Отвечаю ТОЛЬКО по твоим загруженным книгам.
                """;
            sendMenu(chatId, welcomeMsg);
        } else if (text.startsWith(BTN_PREFIX_MODE)) {
            toggleSearchMode(chatId);
        } else if (text.startsWith(BTN_PREFIX_VOICE)) {
            toggleVoiceMode(chatId);
        } else {
            processRequest(chatId, text);
        }
    }

    private void processRequest(long chatId, String userText) {
        sendChatAction(chatId, "typing");
        try {
            updateHistory(chatId, "User", userText);

            String history = String.join("\n", chatHistory.getOrDefault(chatId, new ArrayList<>()));
            SearchMode mode = userSearchMode.getOrDefault(chatId, SearchMode.HYBRID_AI);

            AgentResponse result = orchestrator.processRequest(userText, history, mode);

            String cleanOutput = TextUtil.cleanMarkdown(result.text());
            updateHistory(chatId, "Bot", cleanOutput);

            sendTextWithTimers(chatId, cleanOutput);

            if (result.attachmentPath() != null) {
                File file = new File(result.attachmentPath());
                if (file.exists()) {
                    SendDocument doc = new SendDocument();
                    doc.setChatId(String.valueOf(chatId));
                    doc.setDocument(new InputFile(file));
                    doc.setCaption("🛒 Список покупок");
                    execute(doc);
                }
            }
        } catch (Exception e) {
            sendMenu(chatId, "⚠️ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendTextWithTimers(long chatId, String text) {
        Pattern p = Pattern.compile("\\[\\[TIMER:(.+?)\\]\\]");
        Matcher m = p.matcher(text);
        List<Long> timers = new ArrayList<>();

        while(m.find()) {
            long seconds = TextUtil.parseDuration(m.group(1));
            if (seconds > 0) timers.add(seconds);
        }
        String cleanText = m.replaceAll("").trim();

        if (userVoiceResponse.getOrDefault(chatId, false)) {
            try {
                byte[] audio = speechService.synthesize(cleanText.length() > 500 ? cleanText.substring(0, 500) : cleanText);
                if (audio != null) {
                    File f = File.createTempFile("voice", ".ogg");
                    java.nio.file.Files.write(f.toPath(), audio);
                    SendVoice v = new SendVoice();
                    v.setChatId(String.valueOf(chatId));
                    v.setVoice(new InputFile(f));
                    execute(v);
                    f.delete();
                }
            } catch (Exception e) {}
        }

        sendMenu(chatId, cleanText);
        if (!timers.isEmpty()) sendTimerButtons(chatId, timers);
    }

    private void handleTimerCallback(CallbackQuery q) {
        String data = q.getData();
        if (data.startsWith("START_TIMER:")) {
            long seconds = Long.parseLong(data.split(":")[1]);
            SendMessage msg = new SendMessage(String.valueOf(q.getMessage().getChatId()), "⏳ Таймер: " + TextUtil.formatDuration(seconds));
            try {
                Message sentMsg = execute(msg);
                tools.startUserTimer(this, q.getMessage().getChatId(), sentMsg.getMessageId(), seconds);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void processPhoto(long chatId, List<PhotoSize> photos) {
        sendChatAction(chatId, "upload_photo");
        sendMenu(chatId, "👀 Смотрю на продукты...");
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(photos.get(photos.size() - 1).getFileId());
            String filePath = execute(getFile).getFilePath();
            String fullUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

            File file = FileUtil.downloadFile(fullUrl, "img.jpg");
            com.google.genai.types.Image image = com.google.genai.types.Image.fromFile(file.getAbsolutePath());
            Part imgPart = Part.fromBytes(image.imageBytes().orElseThrow(), "image/jpeg");

            GenerateContentResponse response = genAiClient.models.generateContent(
                    MODEL_NAME, Content.fromParts(Part.fromText("Перечисли продукты списком. Только названия."), imgPart), null
            );

            String products = response.text();
            file.delete();
            sendMenu(chatId, "🔍 Распознано: " + products);
            processRequest(chatId, "У меня есть следующие продукты: " + products + ". Придумай рецепт из них.");
        } catch (Exception e) {
            sendMenu(chatId, "Ошибка фото: " + e.getMessage());
        }
    }

    private void processVoice(long chatId, Voice voice) {
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(voice.getFileId());
            String filePath = execute(getFile).getFilePath();
            String fullUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

            File file = FileUtil.downloadFile(fullUrl, "voice.ogg");
            String text = speechService.transcribe(file);
            if (text != null && !text.isBlank()) {
                sendMenu(chatId, "🗣 Распознано: " + text);
                processRequest(chatId, text);
            } else {
                sendMenu(chatId, "Не удалось распознать речь.");
            }
            file.delete();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void processDocument(long chatId, Document doc) {
        sendMenu(chatId, "📄 Читаю файл...");
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(doc.getFileId());
            String filePath = execute(getFile).getFilePath();
            String fullUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

            File file = FileUtil.downloadFile(fullUrl, doc.getFileName());
            ragService.addDocument(file);
            sendMenu(chatId, "✅ Книга добавлена в базу знаний!");
            file.delete();
        } catch (Exception e) { sendMenu(chatId, "Ошибка: " + e.getMessage()); }
    }

    private void sendMenu(long chatId, String text) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.setParseMode("HTML");
        ReplyKeyboardMarkup k = new ReplyKeyboardMarkup();
        k.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow r1 = new KeyboardRow();
        String currentModeText = (userSearchMode.getOrDefault(chatId, SearchMode.HYBRID_AI) == SearchMode.STRICT_RAG)
                ? "🔒 Строгий (RAG)"
                : "🧠 Гибрид (AI)";

        r1.add(BTN_PREFIX_MODE + currentModeText);

        KeyboardRow r2 = new KeyboardRow();
        r2.add(BTN_PREFIX_VOICE + (userVoiceResponse.getOrDefault(chatId, false) ? "🔊 Вкл" : "🔇 Выкл"));
        r2.add(BTN_HELP);

        rows.add(r1); rows.add(r2);
        k.setKeyboard(rows);
        msg.setReplyMarkup(k);
        try { execute(msg); } catch (Exception e) {}
    }

    private void sendTimerButtons(long chatId, List<Long> timers) {
        SendMessage msg = new SendMessage(String.valueOf(chatId), "Запустить таймер?");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for(Long t : timers) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("▶ " + TextUtil.formatDuration(t));
            btn.setCallbackData("START_TIMER:" + t);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        try { execute(msg); } catch(Exception e){}
    }

    private void sendChatAction(long chatId, String action) {
        try {
            SendChatAction sa = new SendChatAction();
            sa.setChatId(String.valueOf(chatId));
            sa.setAction(ActionType.valueOf(action.toUpperCase()));
            execute(sa);
        } catch(Exception e){}
    }

    private void toggleSearchMode(long chatId) {
        SearchMode current = userSearchMode.getOrDefault(chatId, SearchMode.HYBRID_AI);
        SearchMode next = (current == SearchMode.STRICT_RAG) ? SearchMode.HYBRID_AI : SearchMode.STRICT_RAG;
        userSearchMode.put(chatId, next);

        String desc;
        if (next == SearchMode.STRICT_RAG) {
            chatHistory.remove(chatId);
            desc = "🔒 СТРОГИЙ РЕЖИМ\nКонтекст беседы очищен. Я буду использовать ТОЛЬКО информацию из загруженных файлов/книг.";
        } else {
            desc = "🧠 ГИБРИДНЫЙ РЕЖИМ\nЯ использую базу знаний + общие знания AI.";
        }

        sendMenu(chatId, "✅ Режим установлен: \n" + desc);
    }

    private void toggleVoiceMode(long chatId) {
        boolean newState = !userVoiceResponse.getOrDefault(chatId, false);
        userVoiceResponse.put(chatId, newState);
        sendMenu(chatId, "🎤 Голосовой ответ: " + (newState ? "ВКЛ" : "ВЫКЛ"));
    }

    private void updateHistory(long chatId, String role, String text) {
        chatHistory.putIfAbsent(chatId, new ArrayList<>());
        List<String> h = chatHistory.get(chatId);
        h.add(role + ": " + text);
        if(h.size() > 10) h.remove(0);
    }
}