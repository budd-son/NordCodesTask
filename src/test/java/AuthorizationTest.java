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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Защита API-ключом (X-Api-Key)")
public class AuthorizationTest extends BaseTest {

    private static final String KEY_ERROR = "Missing or invalid API Key";

    @Test
    @Story("Правильный ключ")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("AUTH-1 Правильный ключ → запрос принят")
    @Description("Отправляем LOGIN с правильным X-Api-Key. Сервис должен ответить 200 и result=OK.")
    void validKeyAccepted() {
        assertOk(login());
    }

    static Stream<Arguments> wrongKeys() {
        return Stream.of(
                Arguments.of("AUTH-2", "совсем другой ключ", "wrong_key"),
                Arguments.of("AUTH-3", "тот же ключ в другом регистре", "QAZWSXEDC")
        );
    }

    @ParameterizedTest(name = "{0} {1} → 401")
    @MethodSource("wrongKeys")
    @Story("Неверный ключ")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Ключ чувствителен к регистру. Любой ключ, отличающийся от секретного, " +
            "должен давать 401 и сообщение «Missing or invalid API Key».")
    void wrongKeyRejected(String id, String title, String key) {
        assertError(loginWithKey(key), 401, KEY_ERROR);
    }

    static Stream<Arguments> keysWithSpaces() {
        return Stream.of(
                Arguments.of("AUTH-4", "пробел в конце ключа", "qazWSXedc "),
                Arguments.of("AUTH-5", "пробел в начале ключа", " qazWSXedc")
        );
    }

    static Stream<Arguments> keysWithSurroundingWhitespace() {
        return Stream.of(
                Arguments.of("AUTH-4", "пробел в конце ключа (RFC 7230: OWS отбрасывается)", "qazWSXedc "),
                Arguments.of("AUTH-5", "пробел в начале ключа (RFC 7230: OWS отбрасывается)", " qazWSXedc")
        );
    }

    @ParameterizedTest(name = "{0} {1} → 200")
    @MethodSource("keysWithSurroundingWhitespace")
    @Story("Правильный ключ")
    @Severity(SeverityLevel.NORMAL)
    @Description("Пробелы (OWS) по краям значения заголовка отбрасываются по RFC 7230 " +
            "Ключ остаётся валидным, сервис должен ответить 200.")
    void keyWithSurroundingWhitespaceAccepted(String id, String title, String key) {
        assertOk(loginWithKey(key));
    }
    @Test
    @Story("Ключ не передан")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("AUTH-6 Заголовок X-Api-Key отсутствует → 401")
    @Description("Без заголовка X-Api-Key сервис должен ответить 401 и сообщением «Missing or invalid API Key».")
    void missingKeyRejected() {
        assertError(loginWithKey(null), 401, KEY_ERROR);
    }

    @Test
    @Story("Порядок проверок")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AUTH-7 Неверный ключ + неверный токен → 401 (ключ проверяется первым)")
    @Description("Если неверны и ключ, и токен, сервис не должен рассказывать постороннему про формат токена: " +
            "сначала проверяется ключ.")
    void keyIsCheckedBeforeToken() {
        assertError(apiCall("wrong_key", token + "1", "LOGIN"), 401, KEY_ERROR);
    }

    @Test
    @Story("Запрос с неверным ключом не имеет последствий")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("AUTH-8 Неверный ключ + правильный токен → вход не выполнен")
    @Description("Запрос с неверным ключом не должен обращаться к внешнему сервису /auth " +
            "и не должен создавать сессию: последующий ACTION с правильным ключом даёт 403.")
    void wrongKeyHasNoSideEffects() {
        assertError(apiCall("wrong_key", token, "LOGIN"), 401, KEY_ERROR);
        assertExternalCalls("/auth", 0);
        assertError(action(), 403);
    }

    // ---------------------------------------------------------------- helpers

    private ValidatableResponse loginWithKey(String key) {
        return apiCall(key, token, "LOGIN");
    }

    private ValidatableResponse apiCall(String key, String tokenValue, String action) {
        String shownKey = key == null ? "<не передан>" : "\"" + key + "\"";
        return Allure.step("Отправляем " + action + " с X-Api-Key = " + shownKey, () -> {
            return apiWithKey(key).body(form(tokenValue, action)).when().post().then();
        });
    }
}
