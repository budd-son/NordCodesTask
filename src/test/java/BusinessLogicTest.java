import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Epic("Тестирование веб-сервиса /endpoint")
@Feature("Сессия пользователя: LOGIN → ACTION → LOGOUT")
public class BusinessLogicTest extends BaseTest {

    private String notFound() {
        return "Token '" + token + "' not found";
    }

    @Test
    @Story("Нормальный сценарий")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("BIZ-1 LOGIN → ACTION → 200")
    @Description("После входа пользователь может выполнить действие.")
    void actionAfterLogin() {
        assertOk(login());
        assertOk(action());
    }

    @Test
    @Story("Действия без входа")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("BIZ-2 ACTION без LOGIN → 403")
    @Description("Без предварительного входа действие запрещено.")
    void actionWithoutLogin() {
        assertError(action(), 403, notFound());
    }

    @Test
    @Story("Действия без входа")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-3 LOGOUT без LOGIN → 403")
    @Description("Нельзя выйти из сессии, которой нет.")
    void logoutWithoutLogin() {
        assertError(logout(), 403, notFound());
    }

    @Test
    @Story("Завершение сессии")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("BIZ-4 После LOGOUT действия снова запрещены")
    @Description("LOGIN → ACTION → LOGOUT → ACTION. Последний ACTION должен вернуть 403: сессия удалена.")
    void sessionIsClearedAfterLogout() {
        assertOk(login());
        assertOk(action());
        assertOk(logout());
        assertError(action(), 403, notFound());
    }

    @Test
    @Story("Повторные операции")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-5 Повторный LOGIN тем же токеном → 409")
    @Description("Нельзя войти дважды с одним токеном. Сервис отвечает 409 Conflict.")
    void repeatedLoginRejected() {
        assertOk(login());
        assertError(login(), 409, "Token '" + token + "' already exists");
    }

    @Test
    @Story("Повторные операции")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-6 Повторный LOGOUT → 403")
    @Description("Второй LOGOUT подряд возвращает 403: сессия уже удалена.")
    void repeatedLogoutRejected() {
        assertOk(login());
        assertOk(logout());
        assertError(logout(), 403, notFound());
    }

    @Test
    @Story("Повторные операции")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-7 Несколько ACTION подряд → все 200")
    @Description("Одного входа достаточно для любого количества действий.")
    void manyActionsAfterOneLogin() {
        assertOk(login());
        for (int i = 1; i <= 3; i++) {
            Allure.step("Действие №" + i, () -> assertOk(action()));
        }
    }

    @Test
    @Story("Повторные операции")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-8 Цикл LOGIN → LOGOUT три раза подряд")
    @Description("После выхода тем же токеном можно войти снова — столько раз, сколько нужно.")
    void loginLogoutCycle() {
        for (int i = 1; i <= 3; i++) {
            int round = i;
            Allure.step("Круг №" + round, () -> {
                assertOk(login());
                assertOk(logout());
            });
        }
    }

    @Test
    @Story("Независимость сессий")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("BIZ-9 Сессии разных токенов не влияют друг на друга")
    @Description("Пользователь A вошёл в систему. Пользователь B, который не входил, действовать не может. " +
            "Выход пользователя A не должен затрагивать сессию пользователя B.")
    void sessionsAreIndependent() {
        String otherToken = newToken();
        try {
            assertOk(login());                                           // вошёл A
            assertError(send(otherToken, "ACTION"), 403,
                    "Token '" + otherToken + "' not found");               // B не входил
            assertOk(send(otherToken, "LOGIN"));                         // вошёл B
            assertOk(logout());                                          // вышел A
            assertOk(send(otherToken, "ACTION"));                        // B по-прежнему в системе
        } finally {
            cleanup(otherToken);
        }
    }

    @Test
    @Story("Повторные операции")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("BIZ-10 Отклонённый повторный LOGIN не ломает действующую сессию")
    @Description("Повторный LOGIN вернул 409, но исходная сессия должна остаться рабочей.")
    void rejectedRepeatedLoginKeepsSession() {
        assertOk(login());
        assertError(login(), 409);
        assertOk(action());
    }
}
