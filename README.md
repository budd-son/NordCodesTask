# API Test Task — автотесты для веб-сервиса

Автотесты для тестового задания на вакансию AQA Engineer (Nerd/Codes).

## Стек

Java 17 · JUnit 5 · RestAssured 5.3.2 · WireMock 3.3.1 · Allure 2.24.0 · Maven

## Структура

```
src/test/java/
├── BaseTest.java                          # WireMock, запросы, шаги login/action/logout, очистка
├── AuthorizationTest.java                 # X-Api-Key
├── ValidationTokenTest.java               # формат token
├── ValidationActionTest.java              # допустимые action
├── BusinessLogicTest.java                 # сессия: LOGIN → ACTION → LOGOUT
├── IntegrationWithExternalServerTest.java # обращения к /auth и /doAction (WireMock)
└── HttpLevelTest.java                     # метод, Content-Type, путь, тело
src/test/allure-meta/environment.properties # блок "Environment" в отчёте
TEST_CASES.md                              # описание всех кейсов
BUG_REPORTS.md                             # найденные дефекты
```

## Требования

- JDK 17+, Maven 3.8+
- Свободные порты: `8080` (приложение), `8888` (WireMock — поднимают сами тесты)

## Запуск

1. Запустите приложение (отдельным терминалом, **до** тестов):
   ```bash
   java -jar -Dsecret=qazWSXedc -Dmock=http://localhost:8888/ internal-0.0.1-SNAPSHOT.jar
   ```
2. Запустите тесты:
   ```bash
   mvn clean test                             
   mvn clean test -DexcludedGroups=known-bug   
   mvn test -Dtest=BusinessLogicTest          
   ```
3. Откройте отчёт:
   ```bash
   mvn allure:serve
   ```

## Известные дефекты и «красные» тесты

Часть тестов **падает намеренно** — они доказывают дефекты приложения (см. [BUG_REPORTS.md](BUG_REPORTS.md)).
Такие тесты помечены тегом `known-bug` и ссылкой `BUG-00X` в отчёте. Как только дефект исправят, тест позеленеет сам.

## Как читать Allure-отчёт без знания программирования

- Вкладка **Behaviors** — тесты по темам: Epic → Feature → Story. Названия написаны по-русски.
- Внутри теста — **шаги** («Отправляем запрос LOGIN», «Ожидаем: HTTP 200 и result = OK») и вложения с запросом/ответом.
- Описание теста и его важность (Severity) — в начале карточки.
- Красный тест со ссылкой `BUG-00X` — известный дефект приложения, а не ошибка в тестах.


