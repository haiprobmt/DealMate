# DealMate — Android

Ứng dụng Android của DealMate: tìm **giá mua thực thấp nhất cho đúng sản phẩm** trên Shopee / Lazada /
TikTok Shop. Ứng dụng chỉ hiển thị dữ liệu do backend DealMate (`../backend`) trả về — không có sản
phẩm, giá hay người bán nào được viết sẵn trong app.

- Kotlin · Jetpack Compose · Material 3 · MVVM + Clean Architecture (`data` / `domain` / `ui`)
- Hilt · Coroutines/Flow · Retrofit + OkHttp + kotlinx.serialization · Room · DataStore · WorkManager · Coil
- minSdk 26 · targetSdk 35 · compileSdk 36 · package `com.dealmate.app`
- Ngôn ngữ mặc định: tiếng Việt (`values/`), có tiếng Anh (`values-en/`)

## Yêu cầu

| Công cụ | Phiên bản |
|---|---|
| JDK | 17 trở lên (đã kiểm tra với JDK 21) |
| Android SDK | `platforms;android-36`, `build-tools;35.0.0` |
| Gradle | dùng wrapper đi kèm (8.14.3) |

Tạo `local.properties` (không commit):

```properties
sdk.dir=/duong/dan/toi/android-sdk
```

## Build

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # R8 + shrink resources; chưa ký nếu không có keystore
```

Bản debug có `applicationId` là `com.dealmate.app.debug` nên cài song song được với bản release.

## Cấu hình máy chủ và mã truy cập

Mọi lệnh gọi `/api/v1/*` cần `Authorization: Bearer <token>`.

1. **Trong ứng dụng** (cách thông thường): *Cài đặt → Máy chủ* → nhập địa chỉ máy chủ và mã truy cập →
   **Kiểm tra kết nối** (lưu, rồi gọi `GET /health` và `GET /api/v1/me`). Khi chưa có mã truy cập, các màn
   hình hiển thị trạng thái "Chưa kết nối máy chủ" kèm nút mở Cài đặt.
2. **Địa chỉ mặc định lúc build**: thuộc tính Gradle `dealmate.baseUrl` → `BuildConfig.DEFAULT_BASE_URL`
   (mặc định `http://10.0.2.2:8000/`, tức máy host nhìn từ emulator):

   ```bash
   ./gradlew :app:assembleRelease -Pdealmate.baseUrl=https://dealmate.example.com/
   ```

Lưu ý:

- **Bản release chỉ cho phép HTTPS.** HTTP thường chỉ được bật ở bản debug
  (`src/debug/res/xml/network_security_config.xml`) để chạy với backend trên máy dev/LAN.
- Mã truy cập được mã hoá AES‑256‑GCM bằng khoá trong **Android Keystore** trước khi ghi vào DataStore;
  tệp DataStore và cơ sở dữ liệu cache bị loại khỏi sao lưu/chuyển thiết bị (`res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`). Không có khoá AI hay khoá sàn nào nằm trong app.
- Quyền khai báo trong manifest của app: `INTERNET`, `POST_NOTIFICATIONS`, `CAMERA` (chỉ xin khi bấm
  "Chụp ảnh"). WorkManager và Firebase Messaging tự gộp thêm vài quyền thông thường của thư viện
  (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
  `c2dm.permission.RECEIVE`).

## Kiểm thử

```bash
./gradlew :app:testDebugUnitTest    # unit test JVM + Robolectric (UI Compose, ảnh chụp màn hình)
./gradlew :app:lintDebug
```

Không cần emulator. Bộ test gồm:

| Nhóm | Vị trí (`app/src/test/java/com/dealmate/app/`) |
|---|---|
| Định dạng tiền, thời gian tương đối, link mẫu `.invalid` | `domain/DomainUtilTest.kt` |
| Lọc/sắp xếp giữ thứ tự tầng giá (đã xác minh → ước tính → thiếu phí) | `domain/ResultFiltersTest.kt` |
| Parse JSON theo hợp đồng Pydantic (kể cả `final_price = null`, enum lạ) | `data/DtoParsingTest.kt` |
| Parse phản hồi thật của backend | `data/ContractFixturesTest.kt` |
| Retrofit + MockWebServer: header auth, lỗi 401/429/503…, multipart | `data/ApiTest.kt`, `data/RepositoryTest.kt` |
| Worker thông báo | `data/AlertPollerTest.kt` |
| ViewModel (fake repository, Turbine) | `ui/ViewModelTests.kt` |
| UI Compose trên Robolectric + Hilt (activity thật, repository giả) | `ui/AppFlowTest.kt` |
| Ảnh chụp màn hình (Roborazzi) → `screenshots/` | `ui/ScreenshotTest.kt` |

**Fixture hợp đồng:** thả phản hồi JSON thật của backend vào `app/src/test/resources/contract/`.
`ContractFixturesTest` parse từng tệp theo tiền tố tên: `search_*.json`, `offers_*.json`,
`wishlist_*.json`, `history_*.json`, `sources_*.json`, `explore_*.json`, `assistant_*.json`,
`notifications_*.json`, `alerts_*.json`, `product_*.json`. Thư mục trống thì test vẫn qua.

**Ảnh chụp màn hình:** mỗi lần chạy `testDebugUnitTest`, các màn hình được vẽ lại (sáng + tối) từ dữ liệu
fixture và ghi vào `android/screenshots/*.png`.

Robolectric chạy trên runtime Android 15 (`android-all-instrumented`) do Gradle tải về qua cấu hình
`robolectricRuntime`, nên lúc chạy test không cần mạng.

## Ký bản phát hành

Cấu hình ký `release` được đọc từ **một trong hai** nguồn; nếu không có, `assembleRelease` vẫn chạy và
tạo `app-release-unsigned.apk`.

`keystore.properties` ở thư mục `android/` (đã có trong `.gitignore`):

```properties
storeFile=/duong/dan/dealmate-release.jks
storePassword=...
keyAlias=dealmate
keyPassword=...
```

hoặc biến môi trường:

```bash
export DEALMATE_KEYSTORE_FILE=/duong/dan/dealmate-release.jks
export DEALMATE_KEYSTORE_PASSWORD=...
export DEALMATE_KEY_ALIAS=dealmate
export DEALMATE_KEY_PASSWORD=...
```

Tạo keystore:

```bash
keytool -genkeypair -v -keystore dealmate-release.jks -alias dealmate -keyalg RSA -keysize 4096 -validity 10000
```

## Thông báo giá

Hai đường, độc lập nhau:

1. **Kiểm tra định kỳ (luôn có).** Khi bật *Cài đặt → Thông báo* và đã cấu hình máy chủ, một
   `PeriodicWorkRequest` (~1 giờ, cần mạng) gọi `GET /api/v1/notifications?unread_only=true`, hiện thông báo
   cục bộ (tiêu đề/nội dung do máy chủ gửi; bấm vào mở chi tiết sản phẩm) rồi gọi
   `POST /api/v1/notifications/{id}/read`.
2. **Firebase Cloud Messaging (tuỳ chọn).** App *không* dùng plugin `google-services` và không cần
   `google-services.json`. Thêm đủ bốn thuộc tính Gradle sau (ví dụ trong `~/.gradle/gradle.properties`
   hoặc bằng `-P`) để bật:

   ```properties
   dealmate.firebase.projectId=my-project
   dealmate.firebase.appId=1:1234567890:android:abcdef123456
   dealmate.firebase.apiKey=AIza...
   dealmate.firebase.senderId=1234567890
   ```

   Lấy các giá trị này ở Firebase Console → Project settings → ứng dụng Android có package
   `com.dealmate.app` (thêm `com.dealmate.app.debug` nếu dùng bản debug). Khi có đủ, app tự khởi tạo
   `FirebaseApp`, bật `DealMateMessagingService` và đăng ký token qua `POST /api/v1/devices`. Thiếu bất kỳ
   giá trị nào thì FCM tắt hẳn (không khởi tạo Firebase, service bị vô hiệu), *Cài đặt* ghi rõ "Push (FCM)
   chưa được cấu hình", và việc kiểm tra định kỳ vẫn hoạt động.

   Backend nên gửi **data message** với các khoá (chuỗi): `title`, `body`, và tuỳ chọn `variant_id`,
   `product_id`, `notification_id`, `is_mock`.

## Cấu trúc mã nguồn

```
app/src/main/java/com/dealmate/app/
├── domain/        model, repository interface, usecase (ResultFilters), util (Money, RelativeTime, Listings)
├── data/
│   ├── remote/    DealMateApi (Retrofit), DTO + serializer khoan dung, ServerInterceptor, ánh xạ lỗi
│   ├── local/     Room: tìm kiếm gần đây, kết quả cache, wishlist cache, đã xem gần đây
│   ├── settings/  DataStore + mã hoá token bằng Keystore
│   ├── image/     thu nhỏ / nén ảnh trước khi tải lên
│   └── repository/
├── notifications/ AlertPoller, PriceAlertWorker, PriceAlertNotifier, PushManager + FCM service
├── di/            Hilt modules
└── ui/
    ├── theme/ components/ navigation/ common/
    └── home/ results/ product/ wishlist/ history/ explore/ settings/ assistant/
```

Các nguyên tắc sản phẩm được giữ ở tầng UI:

- Ưu đãi rẻ nhất của sản phẩm khớp nhất (`best`) luôn nằm trên cùng màn hình kết quả.
- `demo_mode = true` → dải thông báo "Chế độ demo — dữ liệu mẫu, không phải giá thật"; mỗi ưu đãi
  `is_mock` có nhãn **DEMO**; link có host kết thúc bằng `.invalid` không bao giờ mở trình duyệt.
- Giá `incomplete` (không có `final_price`) chỉ hiện `known_subtotal` kèm "Chưa gồm phí vận chuyển" và
  không bao giờ được gắn nhãn rẻ nhất — kể cả khi lọc/sắp xếp phía client.
- Nhãn "Mall/Chính hãng" chỉ hiện khi `seller.is_official_store == true` và ghi rõ là nhãn của sàn.
