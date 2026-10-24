package io.github.valerait.ghostlink.core.crypto

import io.github.valerait.ghostlink.core.domain.crypto.LinkCipher
import org.koin.dsl.module

val cryptoModule =
    module {
        single<LinkCipher> { LinkCrypto }
    }
