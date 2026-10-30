import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Проверка параметра action")
public class ValidationActionTest extends BaseTest {

    private static String actionError(String shownValue) {
        return "action: invalid action '" + shownValue + "'. Allowed: LOGIN, LOGOUT, ACTION";
    }

    @Test
    @Story("Допустимые действия")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("ACT-1 LOGIN → 200")
    @Description("LOGIN — допустимое действие: ответ 200 и result=OK.")
    void loginAllowed() {
        assertOk(login());
    }

    @Test
    @Story("Допустимые действия")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("ACT-2 ACTION после LOGIN → 200")
    @Description("ACTION доступен после входа: ответ 200 и result=OK.")
    void actionAllowedAfterLogin() {
        assertOk(login());
        assertOk(action());
    }

    @Test
    @Story("Допустимые действия")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("ACT-3 LOGOUT после LOGIN → 200")
    @Description("LOGOUT завершает сессию: ответ 200 и result=OK.")
    void logoutAllowedAfterLogin() {
        assertOk(login());
        assertOk(logout());
    }

    @Test
    @Story("Недопустимые действия")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("ACT-4 action не передан → 400")
    @Description("Без параметра action сервис отвечает 400 и перечисляет допустимые значения.")
    void missingAction() {
        assertError(sendRaw("token=" + token), 400, actionError("null"));
    }

    static Stream<Arguments> invalidActions() {
        return Stream.of(
                Arguments.of("ACT-5", "строчные буквы «login»", "login", "login"),
                Arguments.of("ACT-6", "неизвестное действие «NOTHING»", "NOTHING", "NOTHING"),
                Arguments.of("ACT-7", "пустая строка", "", "null")
        );
    }

    @ParameterizedTest(name = "{0} {1} → 400")
    @MethodSource("invalidActions")
    @Story("Недопустимые действия")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Допустимы только LOGIN, LOGOUT и ACTION — именно в таком написании. " +
            "Всё остальное должно отклоняться с кодом 400.")
    void invalidActionRejected(String id, String title, String value, String shownInMessage) {
        assertError(send(token, value), 400, actionError(shownInMessage));
    }

    static Stream<Arguments> actionsWithSpaces() {
        return Stream.of(
                Arguments.of("ACT-8", "пробел в начале « LOGIN»", " LOGIN"),
                Arguments.of("ACT-9", "пробел в конце «LOGIN »", "LOGIN ")
        );
    }
    @MethodSource("actionsWithSpaces")
    @ParameterizedTest(name = "{0} {1} → 200 (текущее поведение)")
    @Description("Фиксирует текущее поведение: action с пробелом по краям принимается (trim). " +
            "Требование уточняется.")
    void actionWithSurroundingWhitespace_documentsCurrentBehavior(String id, String title, String value) {
        assertOk(send(token, value));
    }
}
