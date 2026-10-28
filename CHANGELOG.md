# Changelog

## [0.6.0](https://github.com/Valera-IT/Ghostlink/compare/ghostlink-v0.5.0...ghostlink-v0.6.0) (2026-10-28)


### Новое

* **crash:** Crashlytics on Android and iOS (stage 4.5) ([#71](https://github.com/Valera-IT/Ghostlink/issues/71)) ([1542b10](https://github.com/Valera-IT/Ghostlink/commit/1542b105a851adfb421148f7c464459383bf4ef7))


### Документация

* **roadmap:** integer stages 1–10, one Worker section, done items marked ([#75](https://github.com/Valera-IT/Ghostlink/issues/75)) ([126e455](https://github.com/Valera-IT/Ghostlink/commit/126e45521c652a50b0800c87217e68f26e5ebf9f))
* **roadmap:** messenger features, monitoring and cleanup ([#72](https://github.com/Valera-IT/Ghostlink/issues/72)) ([10c9f30](https://github.com/Valera-IT/Ghostlink/commit/10c9f3000a7ef78dc5f21e46cc4603e5b67960e9))

## [0.5.0](https://github.com/Valera-IT/Ghostlink/compare/ghostlink-v0.4.0...ghostlink-v0.5.0) (2026-10-25)


### Новое

* **chat:** photos in chats and groups (stages 6.7–6.8) ([#69](https://github.com/Valera-IT/Ghostlink/issues/69)) ([c676946](https://github.com/Valera-IT/Ghostlink/commit/c6769461e957bfbe0c37908434d254af2681e1bf))
* **data:** MediaStore for chat files via the Worker and B2 (stage 6.5) ([#68](https://github.com/Valera-IT/Ghostlink/issues/68)) ([52b6768](https://github.com/Valera-IT/Ghostlink/commit/52b676878d6b5c418343f3efe474640a47e4fe06))
* **icon:** pixel ghost app icon on Android and iOS ([#63](https://github.com/Valera-IT/Ghostlink/issues/63)) ([ca689fe](https://github.com/Valera-IT/Ghostlink/commit/ca689fe8b457447e33f11780a6068b57179fc8c4))
* **ios:** apps:ios on the shared modules with an Xcode project (stage 4.1) ([#64](https://github.com/Valera-IT/Ghostlink/issues/64)) ([0c5d3be](https://github.com/Valera-IT/Ghostlink/commit/0c5d3bea413f1cc18a1f520a7080e4eb086b5f8e))
* **ios:** local-only expiring clipboard for secrets, native iOS checks (stage 4.3) ([#66](https://github.com/Valera-IT/Ghostlink/issues/66)) ([9e6eda4](https://github.com/Valera-IT/Ghostlink/commit/9e6eda4da38393ed635e4721c728156d6aa8b2f2))
* **ios:** QR scanner on AVFoundation and SecureStore on Keychain (stage 4.2) ([#65](https://github.com/Valera-IT/Ghostlink/issues/65)) ([6e00ef1](https://github.com/Valera-IT/Ghostlink/commit/6e00ef1bf4c955c070616cc0c6b78abadba7e078))
* **profile:** profile photos on Android and iOS (stage 6.9) ([#62](https://github.com/Valera-IT/Ghostlink/issues/62)) ([0c4c4f0](https://github.com/Valera-IT/Ghostlink/commit/0c4c4f02e9d419d3ada5ab61241af8c6dbfd29d5))
* **worker:** signed Backblaze B2 links for chat photos (stage 6.6) ([#67](https://github.com/Valera-IT/Ghostlink/issues/67)) ([a4005d2](https://github.com/Valera-IT/Ghostlink/commit/a4005d2381d5b32a35aa272b7c25c94d81ebe38c))


### Документация

* **roadmap:** iOS becomes stage 4, desktop moves to stage 5 ([#52](https://github.com/Valera-IT/Ghostlink/issues/52)) ([bd57cac](https://github.com/Valera-IT/Ghostlink/commit/bd57cac556c073bd91cd7fc9b049b3bf342ed50f))
* **roadmap:** media via Backblaze B2 instead of R2 in stage 6 ([#60](https://github.com/Valera-IT/Ghostlink/issues/60)) ([ed5110e](https://github.com/Valera-IT/Ghostlink/commit/ed5110e64b019edd374c28e89e7ea2d8417ab104))
* **roadmap:** store readiness stage 6.5 and E2E stage 8 ([#56](https://github.com/Valera-IT/Ghostlink/issues/56)) ([fd103f1](https://github.com/Valera-IT/Ghostlink/commit/fd103f1f43de6446d72a5a882a70e3971e107e91))

## [0.4.0](https://github.com/Valera-IT/Ghostlink/compare/ghostlink-v0.3.0...ghostlink-v0.4.0) (2026-10-24)


### Новое

* **chat:** disappearing messages with a per-chat timer ([#28](https://github.com/Valera-IT/Ghostlink/issues/28)) ([bdc1aa7](https://github.com/Valera-IT/Ghostlink/commit/bdc1aa71036acbd99720dd9be83f3a70474549b8))
* **chat:** show when the partner is typing ([#25](https://github.com/Valera-IT/Ghostlink/issues/25)) ([d2e0a03](https://github.com/Valera-IT/Ghostlink/commit/d2e0a03a8e5e7af1f55f9e95c2b3771eadc4b366))
* **chats:** long-press chat menu to pin, mute and delete a contact ([#24](https://github.com/Valera-IT/Ghostlink/issues/24)) ([8f31b1d](https://github.com/Valera-IT/Ghostlink/commit/8f31b1d63c6705c5b750affdd95f20cf80dd24f2))
* **groups:** group chats with an admin, sender names and read-by-all ticks ([#27](https://github.com/Valera-IT/Ghostlink/issues/27)) ([eefbfc5](https://github.com/Valera-IT/Ghostlink/commit/eefbfc508d8d5ef56a96ba65f455245d23aead90))
* **i18n:** English language with an in-app switch on Android and iOS ([#50](https://github.com/Valera-IT/Ghostlink/issues/50)) ([c14f2cb](https://github.com/Valera-IT/Ghostlink/commit/c14f2cb4e07674bf945fba5e2a1e19bfd6b79de9))
* **ios:** KMP prototype for iOS and desktop with chat parity ([#22](https://github.com/Valera-IT/Ghostlink/issues/22)) ([492f04c](https://github.com/Valera-IT/Ghostlink/commit/492f04c41faebf134239ab10bd7551a90c60e89c))
* **ios:** typing, disappearing messages and group chats in the KMP prototype ([#29](https://github.com/Valera-IT/Ghostlink/issues/29)) ([50e595a](https://github.com/Valera-IT/Ghostlink/commit/50e595a8dd9a8841720208a9559fc459722529be))


### Исправления

* **ios:** long-press chat menu to pin, mute and delete chats ([#30](https://github.com/Valera-IT/Ghostlink/issues/30)) ([003a293](https://github.com/Valera-IT/Ghostlink/commit/003a29352a4a653d40d1bf62c6621cf669511229))

## [0.3.0](https://github.com/Valera-IT/Ghostlink/compare/ghostlink-v0.2.0...ghostlink-v0.3.0) (2026-10-19)


### Новое

* **chat:** "message deleted" placeholder and swipe down to hide keyboard ([#20](https://github.com/Valera-IT/Ghostlink/issues/20)) ([95bd53d](https://github.com/Valera-IT/Ghostlink/commit/95bd53d71c77ab4f052fcd3654bf01f7cd281704))

## [0.2.0](https://github.com/Valera-IT/Ghostlink/compare/ghostlink-v0.1.0...ghostlink-v0.2.0) (2026-10-19)


### Новое

* **chat:** long-press message menu with reply, copy, edit and delete ([#19](https://github.com/Valera-IT/Ghostlink/issues/19)) ([ca7f794](https://github.com/Valera-IT/Ghostlink/commit/ca7f7940a91b475aeb3a5206fee98cfb7d5859c5))
* **chat:** replies to messages ([#15](https://github.com/Valera-IT/Ghostlink/issues/15)) ([c8153f2](https://github.com/Valera-IT/Ghostlink/commit/c8153f2dc76ad28a53a25a663bc3fd70c5e236f1))


### Документация

* ADR 0006 and replies as the first 2a product PR ([e2a65eb](https://github.com/Valera-IT/Ghostlink/commit/e2a65ebcb91897ebbbca429d34d7c6d27a2f442a))
* target architecture, ADRs and updated roadmap ([efd5612](https://github.com/Valera-IT/Ghostlink/commit/efd56125594f3a194b87a84049760eb66e923ffd))
