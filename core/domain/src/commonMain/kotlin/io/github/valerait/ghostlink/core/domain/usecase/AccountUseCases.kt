package io.github.valerait.ghostlink.core.domain.usecase

import io.github.valerait.ghostlink.core.domain.repository.AuthRepository
import io.github.valerait.ghostlink.core.domain.repository.DeviceRepository
import io.github.valerait.ghostlink.core.domain.repository.UserRepository
import io.github.valerait.ghostlink.core.domain.repository.requireUserId
import io.github.valerait.ghostlink.core.model.Account
import io.github.valerait.ghostlink.core.model.AccountKey
import io.github.valerait.ghostlink.core.model.DeviceId
import io.github.valerait.ghostlink.core.model.GhostlinkError
import io.github.valerait.ghostlink.core.model.User
import kotlinx.coroutines.flow.first

/**
 * Новый аккаунт с ключом и профиль. Устройство в реестр запишет DeviceRegistry, как только появится профиль.
 * Аккаунт уже может быть: временный, оставшийся от брошенного входа по QR, — ключ ему выдаст тот же реестр.
 */
class CreateAccountUseCase(
    private val auth: AuthRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(name: String): Account {
        val normalized = requireNotNull(User.normalizeName(name)) { "Name is empty" }
        val account = auth.account.value ?: auth.createAccount()
        users.saveProfile(account.id, normalized)
        return account
    }
}

/** Смена имени профиля по тем же правилам, что и при создании. */
class RenameProfileUseCase(
    private val auth: AuthRepository,
    private val users: UserRepository,
) {
    /** false — имя пустое, ничего не сохранено. */
    suspend operator fun invoke(name: String): Boolean {
        val normalized = User.normalizeName(name) ?: return false
        users.saveProfile(auth.requireUserId(), normalized)
        return true
    }
}

/** Вход по ключу восстановления. */
class RestoreWithRecoveryCodeUseCase(
    private val auth: AuthRepository,
) {
    /** [GhostlinkError.MalformedRecoveryCode] — код с ошибкой, [GhostlinkError.InvalidAccountKey] — ключ не подошёл. */
    suspend operator fun invoke(code: String): Account {
        val key = AccountKey.parseRecoveryCode(code) ?: throw GhostlinkError.MalformedRecoveryCode()
        return auth.signInWithKey(key)
    }
}

/** Ключ восстановления текущего аккаунта; если ключа на устройстве нет, выдаёт новый. */
class GetRecoveryCodeUseCase(
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(): String = (auth.accountKey.value ?: auth.ensureAccountKey()).toRecoveryCode()
}

/** Выход на этом устройстве: «не в сети», запись устройства из реестра, затем сам выход. */
class SignOutUseCase(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val devices: DeviceRepository,
) {
    suspend operator fun invoke(thisDevice: DeviceId) {
        val me = auth.requireUserId()
        users.setOnline(me, false)
        // Без интернета запись устройства останется в списке — её можно удалить с другого устройства.
        runCatching { devices.removeDevice(me, thisDevice) }
        auth.signOut()
    }
}

/**
 * Жёсткая мера: новый секрет ключа. Firebase отзывает сессии остальных устройств,
 * а удаление из реестра заставляет их выйти сразу, не дожидаясь истечения токена.
 */
class SignOutEverywhereUseCase(
    private val auth: AuthRepository,
    private val devices: DeviceRepository,
) {
    /** Возвращает новый ключ восстановления: старый больше не действует. */
    suspend operator fun invoke(thisDevice: DeviceId): String {
        val me = auth.requireUserId()
        val key = auth.rotateKey()
        devices
            .observeDevices(me)
            .first()
            .filter { it.id != thisDevice }
            .forEach { runCatching { devices.removeDevice(me, it.id) } }
        return key.toRecoveryCode()
    }
}
