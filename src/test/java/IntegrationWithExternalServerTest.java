import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Внешний сервис заменён WireMock-ом, поэтому мы точно знаем, ходило ли приложение
 * в /auth и /doAction, и что именно оно туда отправило.
 */
@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Взаимодействие с внешним сервисом")
public class IntegrationWithExternalServerTest extends BaseTest {

    // ------------------------------------------------------------------ /auth

    @Test
    @Story("LOGIN → внешний /auth")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("INT-1 LOGIN обращается к /auth ровно один раз")
    @Description("Успешный вход инициирует ровно один запрос POST /auth к внешнему сервису.")
    void loginCallsAuthOnce() {
        assertOk(login());
        assertExternalCalls("/auth", 1);
    }

    @Test
    @Story("LOGIN → внешний /auth")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("INT-2 В /auth передаётся тело token=<токен>")
    @Description("Приложение передаёт во внешний сервис именно токен пользователя и ничего лишнего.")
    void authRequestBody() {
        assertOk(login());
        Allure.step("Ожидаем: тело запроса к /auth равно token=<токен>", () -> {
            wireMock.verify(postRequestedFor(urlEqualTo("/auth"))
                    .withRequestBody(equalTo("token=" + token)));
        });
    }

    @Test
    @Story("LOGIN → внешний /auth")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("INT-3 Запрос к /auth — формат form-urlencoded, ответ принимается как JSON")
    @Description("Проверяем заголовки Content-Type и Accept в запросе приложения к внешнему сервису.")
    void authRequestHeaders() {
        assertOk(login());
        Allure.step("Ожидаем: Content-Type = application/x-www-form-urlencoded, Accept = application/json", () -> {
            wireMock.verify(postRequestedFor(urlEqualTo("/auth"))
                    .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                    .withHeader("Accept", containing("application/json")));
        });
    }

    @Test
    @Story("Лишние обращения к внешнему сервису")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("INT-4 Невалидный токен → 400, /auth не вызывается")
    @Description("Проверка формата выполняется раньше обращения к внешнему сервису.")
    void invalidTokenDoesNotCallAuth() {
        assertError(send(token + "1", "LOGIN"), 400);
        assertExternalCalls("/auth", 0);
    }

    @ParameterizedTest(name = "INT-5 /auth ответил {0} → LOGIN не выполнен (500), токен не сохранён")
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    @Story("Ошибки внешнего сервиса при входе")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Если внешний /auth вернул ошибку, вход не засчитывается: приложение отвечает 500, " +
            "а последующий ACTION даёт 403 (токен не сохранён). " +
            "Код 500 в этом случае в спецификации не прописан — это наблюдаемое поведение приложения.")
    void authErrorMeansNoLogin(int externalStatus) {
        mockExternal("/auth", externalStatus);
        assertError(login(), 500);
        assertError(action(), 403);
    }

    // -------------------------------------------------------------- /doAction

    @Test
    @Story("ACTION → внешний /doAction")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("INT-6 ACTION обращается к /doAction ровно один раз")
    @Description("Действие после входа инициирует ровно один запрос POST /doAction.")
    void actionCallsDoActionOnce() {
        assertOk(login());
        wireMock.resetRequests();
        assertOk(action());
        assertExternalCalls("/doAction", 1);
    }

    @Test
    @Story("ACTION → внешний /doAction")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("INT-7 В /doAction передаётся тело token=<токен>")
    @Description("Во внешний сервис уходит токен именно того пользователя, который выполняет действие.")
    void doActionRequestBody() {
        assertOk(login());
        assertOk(action());
        Allure.step("Ожидаем: тело запроса к /doAction равно token=<токен>", () -> {
            wireMock.verify(postRequestedFor(urlEqualTo("/doAction"))
                    .withRequestBody(equalTo("token=" + token)));
        });
    }

    @Test
    @Story("Лишние обращения к внешнему сервису")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("INT-8 ACTION без LOGIN → 403, /doAction не вызывается")
    @Description("Без входа приложение отклоняет действие и не беспокоит внешний сервис.")
    void actionWithoutLoginDoesNotCallDoAction() {
        assertError(action(), 403);
        assertExternalCalls("/doAction", 0);
    }

    @Test
    @Story("Лишние обращения к внешнему сервису")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("INT-9 LOGOUT не обращается к внешнему сервису")
    @Description("Выход обрабатывается внутри приложения: после LOGOUT нет новых вызовов /auth и /doAction.")
    void logoutDoesNotCallExternalService() {
        assertOk(login());
        wireMock.resetRequests();
        assertOk(logout());
        assertExternalCalls("/auth", 0);
        assertExternalCalls("/doAction", 0);
    }

    @Test
    @Story("Ошибки внешнего сервиса при действии")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("INT-10 /doAction ответил 500 → ACTION вернул 500, сессия сохранилась")
    @Description("Ошибка внешнего /doAction приводит к 500. Сессия при этом не сбрасывается: после " +
            "восстановления внешнего сервиса ACTION снова даёт 200. " +
            "Поведение сессии в спецификации не описано — тест фиксирует наблюдаемое поведение; " +
            "при уточнении требований его нужно сверить с аналитиком.")
    void doActionErrorKeepsSession() {
        assertOk(login());

        mockExternal("/doAction", 500);
        assertError(action(), 500);

        mockExternal("/doAction", 200);
        assertOk(action());
    }
}
