import com.github.tomakehurst.wiremock.WireMockServer;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Общая инфраструктура:
 *  - поднимает WireMock на :8888 (имитация внешнего сервиса),
 *  - даёт готовые «шаги» (login / action / logout), которые красиво выглядят в Allure,
 *  - гарантирует чистое состояние: у каждого теста свой уникальный токен, после теста — LOGOUT.
 * Глобальные настройки RestAssured намеренно не используются.
 */
public abstract class BaseTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static final String ENDPOINT = "/endpoint";
    protected static final String API_KEY = "qazWSXedc";
    protected static final int MOCK_PORT = 8888;

    protected static WireMockServer wireMock;
    /** Уникальный валидный токен текущего теста. */
    protected String token;

    @BeforeAll
    static void startExternalServiceMock() {
        wireMock = new WireMockServer(MOCK_PORT);
        wireMock.start();
    }

    @AfterAll
    static void stopExternalServiceMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void prepareTest() {
        token = newToken();
        wireMock.resetAll();                // сбрасываем и заглушки, и журнал вызовов
        mockExternal("/auth", 200);
        mockExternal("/doAction", 200);
    }

    @AfterEach
    void cleanUpSession() {
        cleanup(token);
    }

    // ------------------------------------------------------------------ запросы

    private static RequestSpecification plainRequest(String apiKey) {
        RequestSpecification spec = given()
                .baseUri(BASE_URL)
                .basePath(ENDPOINT)
                .contentType("application/x-www-form-urlencoded")
                .accept("application/json");
        if (apiKey != null) {
            spec.header("X-Api-Key", apiKey);
        }
        return spec;
    }

    /** Запрос с правильным API-ключом. Запрос и ответ автоматически попадают в Allure. */
    protected static RequestSpecification api() {
        return apiWithKey(API_KEY);
    }

    /** Запрос с произвольным ключом; {@code null} — заголовок X-Api-Key не отправляется. */
    protected static RequestSpecification apiWithKey(String apiKey) {
        return plainRequest(apiKey).filter(new AllureRestAssured());
    }

    protected static String form(String token, String action) {
        return "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&action=" + URLEncoder.encode(action, StandardCharsets.UTF_8);
    }

    /** Отправляет запрос вида token=...&action=... */
    protected ValidatableResponse send(String tokenValue, String action) {
        return Allure.step("Отправляем запрос " + action, () -> {
            return api().body(form(tokenValue, action)).when().post().then();
        });
    }

    /** Отправляет произвольное тело (для случаев, когда параметр должен отсутствовать). */
    protected ValidatableResponse sendRaw(String body) {
        return Allure.step("Отправляем запрос с телом: " + (body.isEmpty() ? "<пусто>" : body), () -> {
            return api().body(body).when().post().then();
        });
    }

    protected ValidatableResponse login() {
        return send(token, "LOGIN");
    }

    protected ValidatableResponse action() {
        return send(token, "ACTION");
    }

    protected ValidatableResponse logout() {
        return send(token, "LOGOUT");
    }

    // --------------------------------------------------------------- проверки

    protected static void assertOk(ValidatableResponse response) {
        Allure.step("Ожидаем: HTTP 200 и result = OK", () -> {
            response.statusCode(200).body("result", equalTo("OK"));
        });
    }

    protected static void assertError(ValidatableResponse response, int status) {
        Allure.step("Ожидаем: HTTP " + status + " и result = ERROR", () -> {
            response.statusCode(status).body("result", equalTo("ERROR"));
        });
    }

    protected static void assertError(ValidatableResponse response, int status, String message) {
        Allure.step("Ожидаем: HTTP " + status + ", result = ERROR, message = \"" + message + "\"", () -> {
            response.statusCode(status)
                    .body("result", equalTo("ERROR"))
                    .body("message", equalTo(message));
        });
    }

    // ----------------------------------------------------- внешний сервис (мок)

    protected static void mockExternal(String path, int status) {
        wireMock.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));
    }

    protected static void assertExternalCalls(String path, int times) {
        Allure.step("Ожидаем: внешний сервис " + path + " вызван " + times + " раз(а)", () -> {
            wireMock.verify(times, postRequestedFor(urlEqualTo(path)));
        });
    }

    // ----------------------------------------------------------------- прочее

    /** Случайный токен: 32 символа, только 0-9A-F. */
    protected static String newToken() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /** Тихий LOGOUT (без вложений в отчёт), чтобы не оставлять сессии между тестами. */
    protected static void cleanup(String tokenValue) {
        try {
            plainRequest(API_KEY).body(form(tokenValue, "LOGOUT")).when().post();
        } catch (Exception ignored) {
            // приложение может быть недоступно — это не должно маскировать результат теста
        }
    }
}
