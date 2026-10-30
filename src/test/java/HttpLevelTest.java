import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Нестандартные HTTP-запросы")
public class HttpLevelTest extends BaseTest {

    @Test
    @Story("Неверный метод")
    @Severity(SeverityLevel.MINOR)
    @Issue("BUG-004")
    @Tag("known-bug")
    @DisplayName("HTTP-1 GET вместо POST → 405")
    @Description("Эндпоинт принимает только POST. На GET ожидаем 405 Method Not Allowed. " +
            "ИЗВЕСТНЫЙ ДЕФЕКТ BUG-004: сервис отвечает 500.")
    void getMethodNotAllowed() {
        Allure.step("Отправляем GET /endpoint", () -> {
            api().when().get().then().statusCode(405);
        });
    }

    @Test
    @Story("Неверный формат запроса")
    @Severity(SeverityLevel.MINOR)
    @Issue("BUG-004")
    @Tag("known-bug")
    @DisplayName("HTTP-2 Content-Type: application/json → 415 (или 400)")
    @Description("Сервис ждёт form-urlencoded. На JSON ожидаем 415 Unsupported Media Type (допустим 400). " +
            "ИЗВЕСТНЫЙ ДЕФЕКТ BUG-004: сервис отвечает 500.")
    void jsonContentTypeRejected() {
        Allure.step("Отправляем JSON вместо form-urlencoded", () -> {
            api().contentType("application/json")
                    .body("{\"token\":\"" + token + "\",\"action\":\"LOGIN\"}")
                    .when().post().then()
                    .statusCode(anyOf(is(415), is(400)));
        });
    }

    @Test
    @Story("Несуществующий путь")
    @Severity(SeverityLevel.MINOR)
    @Issue("BUG-004")
    @Tag("known-bug")
    @DisplayName("HTTP-3 POST /unknown → 404")
    @Description("На несуществующий адрес ожидаем 404 Not Found. " +
            "ИЗВЕСТНЫЙ ДЕФЕКТ BUG-004: сервис отвечает 500.")
    void unknownPathNotFound() {
        Allure.step("Отправляем POST /unknown", () -> {
            api().basePath("/unknown").body(form(token, "LOGIN"))
                    .when().post().then().statusCode(404);
        });
    }

    @Test
    @Story("Неверный формат запроса")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("HTTP-4 Пустое тело запроса → 400")
    @Description("Без параметров token и action запрос некорректен: ожидаем 400 и result=ERROR.")
    void emptyBodyRejected() {
        assertError(sendRaw(""), 400);
    }

    @Test
    @Story("Формат ответа")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("HTTP-5 Ответ приходит в формате JSON")
    @Description("Успешный ответ и ответ с ошибкой имеют Content-Type application/json.")
    void responseIsJson() {
        ValidatableResponse ok = login();
        Allure.step("Успешный ответ: Content-Type содержит application/json", () -> {
            ok.header("Content-Type", containsString("application/json"));
        });
        ValidatableResponse error = sendRaw("");
        Allure.step("Ответ с ошибкой: Content-Type содержит application/json", () -> {
            error.header("Content-Type", containsString("application/json"));
        });
    }
}
