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
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.stream.Stream;

@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Проверка формата токена")
public class ValidationTokenTest extends BaseTest {

    /**
     * Текст ошибки, который отдаёт приложение сейчас. Обратите внимание: в нём «0-9A-F»,
     * а по спецификации токен — это «A-Z0-9». Это и есть BUG-001.
     */
    private static final String FORMAT_ERROR = "token: должно соответствовать \"^[0-9A-F]{32}$\"";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Токен ровно из 32 символов — основа для построения «испорченных» токенов. */
    private static final String BASE = "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4";

    @Test
    @Story("Корректные токены")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TOKEN-1 Токен из 32 символов (цифры и буквы A-F) → принят")
    @Description("Токен длиной 32 символа из набора A-Z0-9 проходит проверку. LOGIN возвращает 200 и result=OK.")
    void validToken() {
        assertOk(login());
    }

    static Stream<Arguments> invalidTokens() {
        String shortened = BASE.substring(0, 31);
        return Stream.of(
                Arguments.of("TOKEN-2", "слишком длинный (33 символа)", BASE + "5"),
                Arguments.of("TOKEN-3", "слишком короткий (31 символ)", shortened),
                Arguments.of("TOKEN-4", "пустая строка", ""),
                Arguments.of("TOKEN-5", "пробел в конце (32 символа вместе с пробелом)", shortened + " "),
                Arguments.of("TOKEN-6", "пробел в начале (32 символа вместе с пробелом)", " " + shortened),
                Arguments.of("TOKEN-7", "кириллическая буква «Ф» вместо латинской", BASE.substring(0, 10) + "Ф" + BASE.substring(11)),
                Arguments.of("TOKEN-8", "строчные буквы", BASE.toLowerCase()),
                Arguments.of("TOKEN-9", "спецсимвол «_»", shortened + "_")
        );
    }

    @ParameterizedTest(name = "{0} {1} → 400")
    @MethodSource("invalidTokens")
    @Story("Некорректные токены")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Токен, не подходящий под формат (32 символа, только A-Z0-9, заглавные), " +
            "должен отклоняться с кодом 400 и понятным сообщением. Во всех случаях длина проверяемой " +
            "строки подобрана так, чтобы ломался только один критерий.")
    void invalidTokenRejected(String id, String title, String badToken) {
        assertError(send(badToken, "LOGIN"), 400, FORMAT_ERROR);
    }

    @Test
    @Story("Некорректные токены")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TOKEN-10 Токен не передан → 400")
    @Description("Без параметра token сервис отвечает 400 и сообщает, что токен обязателен.")
    void missingToken() {
        assertError(sendRaw("action=LOGIN"), 400, "token: не должно равняться null");
    }

    @ParameterizedTest(name = "TOKEN-11 Недопустимый токен при действии {0} → 400")
    @ValueSource(strings = {"ACTION", "LOGOUT"})
    @Story("Некорректные токены")
    @Severity(SeverityLevel.NORMAL)
    @Description("Формат токена проверяется для любого действия, а не только для LOGIN.")
    void invalidTokenRejectedForAnyAction(String action) {
        assertError(send(BASE.substring(0, 31), action), 400, FORMAT_ERROR);
    }

    // ----------------------------------------------------------- BUG-001

    static Stream<Arguments> tokensValidBySpecification() {
        return Stream.of(
                Arguments.of("TOKEN-12a", "содержит букву G (первая после F)", "G" + newToken().substring(1)),
                Arguments.of("TOKEN-12b", "содержит букву Z (последняя в алфавите)", "Z" + newToken().substring(1)),
                Arguments.of("TOKEN-12c", "случайный токен из всего набора A-Z0-9", randomToken())
        );
    }

    @ParameterizedTest(name = "{0} Токен {1} → должен быть принят")
    @MethodSource("tokensValidBySpecification")
    @Story("Расхождение со спецификацией")
    @Severity(SeverityLevel.CRITICAL)
    @Issue("BUG-001")
    @Tag("known-bug")
    @Description("По документации токен — 32 символа из набора A-Z0-9, то есть допустимы ВСЕ латинские буквы. " +
            "ИЗВЕСТНЫЙ ДЕФЕКТ BUG-001: приложение принимает только 0-9 и A-F (шестнадцатеричные), " +
            "поэтому токены с буквами G-Z отклоняются с кодом 400. Тест упадёт, пока дефект не исправлен.")
    void tokenValidBySpecAccepted(String id, String title, String specToken) {
        try {
            assertOk(send(specToken, "LOGIN"));
        } finally {
            cleanup(specToken);
        }
    }

    /** Случайный токен из полного набора A-Z0-9, в котором гарантированно есть буква G-Z. */
    private static String randomToken() {
        StringBuilder sb = new StringBuilder("Q");   // Q — не шестнадцатеричная буква
        for (int i = 1; i < 32; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
