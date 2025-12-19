# KitchenBot

Телеграм-бот-су-шеф, который помогает от идеи блюда до подачи на стол. Использует Gemini, RAG по вашим загруженным файлам и умеет работать с фото и голосом.

## Возможности
- генерация рецептов по запросу или по списку ингредиентов
- распознавание продуктов на фото
- ведение списка покупок и отправка его файлом
- голосовое управление и ответы голосом
- таймеры для этапов готовки
- поиск по загруженным файлам рецептов (RAG) с переключением между гибридным и строгим режимами

## Требования
- JDK 17 и Maven 3+;
- созданный бот в Telegram: `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN`;
- ключ Google Gemini: `GEMINI_API_KEY`;
- ключ для синтеза/распознавания речи Salute: `SALUTE_API_KEY`.

## Настройка окружения
Создайте файл `.env` в корне проекта (он подхватывается через `Dotenv`) и заполните переменные:

```
TELEGRAM_BOT_USERNAME=your_bot_name
TELEGRAM_BOT_TOKEN=your_bot_token
GEMINI_API_KEY=your_gemini_key
SALUTE_API_KEY=your_salute_key
```

При необходимости поменяйте модель Gemini в `src/main/resources/application.properties` (`gemini.model.name`).

## Запуск

```bash
mvn spring-boot:run
```

После старта добавьте бота в Telegram и отправьте `/start`, чтобы увидеть меню и кнопки режимов (RAG/AI и голосовой ответ).
