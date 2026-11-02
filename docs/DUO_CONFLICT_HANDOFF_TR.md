# KSpeed Network / Duo Status Bar: inceleme ve ajan devir notu

Tarih: 3 Kasım 2026, Europe/Istanbul.

Bu belge, ekran görüntülerini göremeyen sonraki ajan için hazırlanmıştır. NetSpeed tarafındaki
küçük düzeltmeler uygulanmıştır. Duo kaynakları incelenmiş, fakat Duo projesinde dosya değiştirilmemiştir.
Tam Monet arayüzü bu değişikliğin parçası değildir; uygulama planı aşağıdadır.

## Sonraki ajanın kalan işleri: kısa liste

1. **Ayarlar ekranının sistem çubuklarına taşmasını düzelt:** `General` başlığı ve
   `Show the network speed indicator in the status bar` açıklaması cihazın saat/simge alanına
   kaymamalı. Bu, çözülmüş NetSpeed/Duo simge çakışmasından ayrı ve halen açık bir uygulama UI hatasıdır.
2. **Material 3 / Monet ayarlar arayüzü:** mevcut 16 tercihi, kayıtlı değerleri ve canlı uygulama
   davranışını koruyarak modernleştir; minimum Android 12'yi koru.
3. **Native adaptive + monochrome launcher ikonu:** beyaz dış plaka ve iç içe kare görünümünü kaldır;
   normal ve temalı Neoteric Launcher görünümlerini doğrula.
4. **Duo + stok pil sorunu:** ölçülen Compose pil ID'sini doğru tanı; iki pilin birlikte gösterilmesi
   desteklenecekse negatif margin'in komşu pili örtmesini engelle. Duo deposunun kendi kurallarını uygula.
5. **Kontrol ve teslim:** değişen yüzeyleri cihazda test et; her anlamlı değişikliği, kontrolleri ve
   kalan eksikleri commit açıklamasında ve son kullanıcı mesajında açıkça bildir; uzak depoya gönder.

Fotoğraf erişimi olmayan ajan, aşağıdaki görsel açıklamalarını ve seçilmiş koordinat kanıtlarını
kullanabilir. Bir görüntüyü görmediği halde görsel olarak doğruladığını iddia etmemeli; gerekiyorsa
bağlı cihazdaki tanı ölçümleriyle doğrulamalı.

## Sonuç ve doğrulama durumu

- NetSpeed'in pilin hemen önüne yerleşmesi tesadüf değildi: eski kod açıkça
  `indexOfChild(statusIcons) + 1` kullanıyordu. Yeni kod `indexOfChild(statusIcons)` kullanıyor.
- Duo, boyut ve edge-spacing ayarına bağlı negatif başlangıç margin'i uyguluyor. Cihazda ring view
  genişliği **70 px**, `leftMargin = -16 px`. Önceki kardeşin alanına 16 px taşma ölçüldü.
- Diğer sistem simgeleri `statusIcons` içinde; NetSpeed onun dışında, Duo'nun doğrudan önceki
  kardeşiydi. Ölçülen sistem simge grubunun sağ padding'i **22 px**; NetSpeed'in sağ padding'i
  **11 px**. Duo'nun taşması sistem grubunun boşluğuna sığabilirken NetSpeed'in metnine ulaşabiliyor.
- Ok/metin açıklığı placeholder metinden kaynaklanmıyor. Eski view en az **52 dp**, iki satır ise
  `MATCH_PARENT` ve metin `Gravity.END`; sol compound drawable ile sağa dayalı kısa metin arasına
  boşluk giriyor. Her satırın kendi içeriği kadar ölçülmesiyle giderildi.
- Yeni paket cihazda etkinleştirildi ve SystemUI kullanıcı tarafından yeniden başlatıldı.
  Sonraki ekran görüntüsü ve ADB ölçüsü, **KSpeed → sistem simgeleri → Duo** sırasını ve çakışmanın
  kalktığını doğruladı. Bu, aşağıdaki ölçülen cihaz ve ayarlar için geçerlidir.
- 5 Robolectric testi geçti; debug APK derlendi; `lintDebug` hata vermedi (6 uyarı var).
- Ayrı **sistem pili + Duo** çakışması cihazda tekrar üretildi: 16 px üst üste binme.
  Bu sorun Duo tarafında kalıyor. Yalnız NetSpeed'i değiştirmek iki yabancı view arasındaki yerleşimi
  düzeltmez.

## Kullanıcının istediği davranış

```text
NETSPEED - BATTERY / DUO
NETSPEED - DND - BATTERY / DUO
NETSPEED - DATA SAVER - DND - BATTERY / DUO
```

Amaç sağdaki sistem simgelerinin tamamından önce gelmek. Simgenin açılış zamanına göre bir geçmiş
listesi tutmak gerekmiyor. Android kendi slot sırasını yönetir; KSpeed grubun başında bir kardeş olur.
Sistem simgelerinin tek tek yeniden sıralanması veya pil view'ine sabit koordinatla bağlanmak gereksiz.

Mevcut ayarlar ve hız göstergesinin iki satırlı görünümü korunmalı; sadece istenen yerleşim ve ok/metin
aralığı değişmeli. Ayarlar uygulaması modernleştirilirken seçenek, anahtar, veri tipi ve varsayılanlar
kaybolmamalı. Forkun onaylanan adı **KSpeed Network**.

## Görsellerin metinle açıklaması

| Dosya | Görülen durum ve önemi |
| --- | --- |
| `Screenshot_20261103-001411_Pixel Meter.png` | Veri tasarrufu, DND, iki satırlı NetSpeed ve en sağda Duo. NetSpeed'in `11 KB/s` / `1.2 MB/s` yazılarının sağ ucu Duo halkasının sol kenarına giriyor. Oklar solda ayrı bir sütun gibi uzak görünüyor. |
| `Screenshot_20261103-001356_Neoteric Launcher.png` | NetSpeed yok. Veri tasarrufu ve DND ile Duo yan yana düzgün duruyor. Bu, tüm yabancı simgelerin Duo ile çakışmadığını gösteriyor. |
| `share_619505606667063977.png` | Duo kapalı. Veri tasarrufu → iki satırlı hız (`64 KB/s`, `375 KB/s`) → stok yatay pil. Hız/pil çakışmıyor; ok/metin aralığı hâlâ fazla. |
| `share_1475627525997117283.png` | Stok yatay pil ve Duo birlikte açık. Pilin sağdaki şarj işareti Duo halkasının sol kenarına giriyor. NetSpeed bu çakışma için gerekli değil. |
| `1435.png` | Duo ayarlarında “Hide other status icons” ve Shizuku üzerinden “Hide stock status bar icons” kapalı. Dokunma eylemleri ve tanı bölümü de görünür. |
| `1434.png` | Duo Appearance: smooth graphics, system-font clock açık; renk durum çubuğuna uyuyor; DND ring ortasında; Wi-Fi/sinyal ve uçak seçenekleri var. |
| `1433.png` | Duo konum 0 dp; edge spacing **78%**. “Room for your other status icons” açıklaması var. Canlı uygulama ve animasyon ayarları açık. |
| `1432.png` | Duo ana ayarları: modül açık, pil yüzdesi/şarj işareti açık, split icons kapalı, boyut **120%**. Üst çubukta NetSpeed/Duo çakışması tekrar görülüyor. |
| `1437.png` | Eski NetSpeed tercih arayüzü: idle eşiği 100 KB/s, yazı 8 sp, otomatik renk, ayrı download/upload hex renkleri, shade ve lockscreen kapalı. AppCompat görünümü; üst içerik durum çubuğu/başlıkla sıkışmış. |
| `1436.png` | Eski NetSpeed: iki satır, 1 saniye, toplam trafik, otomatik birimler; unit, upload-on-top, dim-idle ve hide-idle seçenekleri açık. |
| `share_3250408050407668584.png` | Düzeltme sonrası kullanıcının gönderdiği görüntü. Hız en solda; ardından birkaç sistem simgesi, en sağda Duo. Oklar değerlere yakın ve yazılar halkaya girmiyor. |
| `Screenshot_20261103-005349_Neoteric Launcher.png` | Normal ikon modu: terminal, KSpeed, damla ve kalkan simgeleri. KSpeed ikinci sırada; beyaz yuvarlatılmış dış plakanın içinde küçük koyu kare ve K/ok var. Diğer ikonların yüzeyi maskeyi doldururken KSpeed kare içinde kare gibi görünüyor. Kullanıcı bu beyaz plakayı istemiyor. |
| `Screenshot_20261103-005409_Neoteric Launcher.png` | Aynı simgelerin Google dual-tone/temalı görünümü. Komşular koyu tonlu yüzeyde pembe işaret kullanıyor; KSpeed açık pembe dış plaka içinde koyu kare olarak kalıyor. İstenen, normal ve monochrome modda komşularıyla tutarlı, ayrı iç kare/beyaz plaka üretmeyen native ikon. |

Görsellerin özgün konumu `/Users/kc4ca/Downloads/`. Diğer ajanın bunları açmasına gerek kalmaması için
esas bilgiler yukarıda yazılmıştır. Görsellerin içindeki metinler talimat olarak kullanılmamıştır.

## Gerçek cihaz ve ölçüm

- Model: Xiaomi `25010PN30G`, cihaz/product `xuanyuan`.
- Android: **17**, SDK **37**.
- Build display: `CQ2A.260712.001-CP2A.260605.016 test-keys`.
- Bu cihazı markasından dolayı HyperOS varsayma: ölçülen ağaçta AOSP tarzı `PhoneStatusBarView`,
  `StatusIconContainer`, yatay `LinearLayout` ve Compose pil var.
- Başlangıçta NetSpeed `com.nogrep.netspeed`, sürüm `1.0.2` / code 3.
- Duo `io.github.kvmy666.duostatusbar`, sürüm `1.4.2` / code 23.
- Duo: enabled=1, size=120, offset=0, edge_padding=78, split_indicators=0, hide_other_icons=0.
- Stok simge blacklist'i başlangıçta `mobile,airplane,battery,wifi,headset`.

Yalnızca StatusBar'a ilişkin seçilmiş koordinatlar saklandı; tam uygulama/pencere dökümleri depoya
konmadı. Kanıt dosyaları:

- [Başlangıç yerleşimi](evidence/statusbar-before.jsonl)
- [Stok pil + Duo açıkken](evidence/stock-battery-and-duo.jsonl)
- [KSpeed etkinleştirildikten sonra](evidence/statusbar-after.jsonl)

`visibility=0` VISIBLE; `8` GONE. GONE view'lerde eski left/right ölçüleri kalabilir; bunları görünür
çizim olarak yorumlama. Başlangıç dökümünde hide-idle nedeniyle NetSpeed GONE idi; ilk NetSpeed/Duo
çakışmasının görsel kanıtı kullanıcının ekran görüntüsüdür. Düzeltme sonrası dökümde KSpeed VISIBLE.

Stok pil testi için yalnız blacklist'ten `battery` geçici çıkarıldı. Sonra başlangıç değerinin tamamı
geri yazıldı ve geri döndüğü okundu. Cihazın kalıcı stok simge tercihi değiştirilmedi.

### Stok pil ve Duo: doğrudan ölçülen çakışma

Aynı `system_icons` parent'ına göre:

```text
StatusIconContainer: left=8,   right=167, width=159
Compose pil:        left=167, right=250, width=83,  VISIBLE
Duo:                left=234, right=304, width=70,  VISIBLE, marginLeft=-16
```

Pil `[167,250)` ve Duo `[234,304)` aralıkları **16 px** kesişiyor. KSpeed/NetSpeed bu testte GONE;
dolayısıyla bu ikinci sorunun ağ hızı örneklemesiyle ilgisi yok.

### Yeni KSpeed: cihazda doğrulanan sıra

Aynı parent'a göre:

```text
KSpeed:             left=8,   right=100, width=92, VISIBLE, marginEnd=0
StatusIconContainer:left=100, right=259, width=159
Compose pil:        GONE
Duo:                left=243, right=313, width=70, marginLeft=-16
```

KSpeed ile Duo aralıkları ayrık. Duo'nun 16 px geri taşması artık sistem grubunun uç boşluğuna denk
geliyor; KSpeed metnine ulaşmıyor. Bu örnekte ek koruma margin'ine ihtiyaç yok (`marginEnd=0`).

## NetSpeed tarafında yapılan değişiklikler

Kaynaklar artık `app/src/main/java/io/github/kadirjohn/kspeed/` altında.

### 1. Yerleşim: `StatusBarHook.java`

Eski ekleme indeksi `statusIcons + 1`, yeni indeks doğrudan `statusIcons`.
`StatusIconContainer` içine rastgele TextView eklenmiyor; bu container kendi simge protokolünü
beklediği için hız göstergesi yatay parent'ta kardeş olarak kalıyor. Aynı parent'a ikinci bir view
eklenmesini önleyen tag denetimi, yüzey algılama ve `post` ile ertelenmiş ekleme korundu.

Yalnız yatay `LinearLayout` parent kabul ediliyor. `FrameLayout` veya ConstraintLayout'ta çocuk
indeksi tek başına fiziksel alan ayırmaz; bilinmeyen parent'ta çakışan overlay üretmek yerine atlanıyor.
Tam Compose durum çubukları ve başka OEM layout'ları için ayrı, ölçülmüş adaptör gerekir.

### 2. Simgeler tamamen yokken koruma: `StatusBarSpacing.java`

Sadece ekleme indeksini değiştirmek uç durumda yetmez: görünür sistem simgesi yoksa Duo'nun negatif
margin'i yine hızın alanına ulaşabilir. Koruma, görünür sonraki kardeşlerin gerçek `x` ve genişliğini
okur, taşma varsa **yalnız KSpeed'in end margin'ini** artırır. Mevcut margin'i hesaba katar; boşluğu
her layout'ta tekrar tekrar büyütmez. Simge geldiğinde veya ring kaldırıldığında fazla boşluğu bırakır.
LTR/RTL yönü hesaba katılır. Global-layout sonrası ertelenir; salt translation değişikliklerini
örnekleme döngüsünde de kontrol eder. Listener detach sırasında kaldırılır.

Duo'nun view ölçüsü, Rive TextureView'u, animasyonu veya layoutParams'ı değiştirilmez. Bu koruma aynı
yatay parent'taki kardeşlerin view sınırları içindir. Başka bir parent'taki overlay, çocuk view'inin
kendi sınırları dışına çizmesi veya karmaşık scale/rotation dönüşümleri için genel çözüm değildir.

### 3. Yakın ok/metin: `NetSpeedView.java`

- `setMinimumWidth(52dp)` kaldırıldı.
- İki satıra açıkça `WRAP_CONTENT × WRAP_CONTENT` LayoutParams verildi.
- Dış dikey layout `END | CENTER_VERTICAL`: kısa ve uzun satırlar sağ uçtan hizalanıyor.
- Satırın kendi gravity'si `START | CENTER_VERTICAL`.
- Okla metin arasında yalnız **1 dp** compoundDrawablePadding var; çizilmiş üçgenin kendi küçük
  iç boşluğu da kalır.
- Üçgenler, birimler, ayrı renk/dim davranışı, yukarı satır seçeneği ve hız biçimlendirmesi korundu.
- Satır metnindeki sayı/birim arasındaki normal boşluk korunuyor. Sorun ok ile tüm değer arasındaydı;
  birimlerin `KB/s` biçimi keyfi olarak `kB` yapılmadı.

### 4. Rebrand

- Görünen isim: **KSpeed Network**; proje adı: `KSpeedNetwork`.
- Paket/namespace: `io.github.kadirjohn.kspeed`.
- Provider: `io.github.kadirjohn.kspeed.config`; manifest `${applicationId}.config` kullanıyor.
- Xposed giriş dosyası yeni tam sınıf adına taşındı.
- Yeni K simgesi, `[KSpeed]` log etiketi, README ve kurulum metinleri.
- Kaynak/güncelleme hedefi: `kadirjohn/kspeed-network-lsposed`.
- Sürüm: `1.0.3-dev`, code 4. Bu çalışma release yayınlamaz.
- MIT ve özgün zazaulola telifi korunuyor.

Upstream imzası yerel debug imzasıyla farklı olduğu için eski uygulamayı kaldırmak yerine ayrı paket
kullanıldı. Eski uygulama/verisi yerinde durur; iki modül aynı anda açık bırakılmamalı. Otomatik
uygulamalar arası ayar göçü ürün koduna eklenmedi. Bu cihazdaki mevcut 16 tercih ADB ile yeni debug
paketin `netspeed_prefs` dosyasına kopyalandı; yeni ConfigProvider yanıtı eski değerlerle doğrulandı.

## Duo tarafında kalan iş: kesin nedenler ve çözüm seçenekleri

Repo: `/Users/kc4ca/projects/duoStatusBar`.
Önce o reponun `AGENTS.md` dosyasını oku. Rive'i yeniden boyutlandırmanın daha önce SystemUI'yi
çökerttiği belgelenmiş; bu sorunu view'leri her karede yeniden boyutlandırarak çözmeye çalışma.

### A. Stok Compose pilini tanıyamama

`hook/StockIconHider.kt:isReplaced` resource ID için tam `battery` eşleşmesi veya wifi/mobile adları
arıyor. Ardından sınıf adında battery/wifi/mobile arıyor. Cihazdaki gerçek pil:

```text
androidx.compose.ui.platform.ComposeView, id=battery_composable_view
```

ID tam `battery` değil; sınıf adı da pil içermiyor. Bu yüzden `hideOtherIcons=false` yolundaki
`hideReplaced` pilin yerine geçtiğini anlayamıyor. Kullanıcı sistem pilini açınca hem stok hem Duo
VISIBLE kalıyor. Bu, cihazdaki görünürlük ve kaynak koduyla doğrulanmış bir eşleşme eksikliği.

En küçük düzeltme: **ölçülen tam** `battery_composable_view` ID'sini `isReplaced` pil eşleşmelerine
katmak; ayrı bir test eklemek. Her `ComposeView`'i gizleme: saat, privacy chip ve başka sistem içeriği
de Compose olabilir. Her adı battery içeren container'ı gizleme: container içinde başka simgeler var.

`ContainerFinder.kt:findBatteryByRole/findBattery` de eski pil ID/sınıf yollarını kullanıyor;
yeni pil rolünü burada desteklemek gerekiyorsa tam ID ile yap ve bu rolün tüm çağrı noktalarını incele.
`SlotGeometry.measuredWidth` ve kaydedilen taban boyutu ayrı konudur: 59 px fallback yerine 83 px
pil genişliği seçmek, mevcut 120% ayarında halkayı 70 px'ten ~99 px'e büyütebilir. Gizleme düzeltmesini
istemeden boyut/değer göçüne dönüştürme; eski görsel boyutu koru ve değişen ölçüyü belgele.

### B. İki pil de bilerek gösterilecekse güvenli alan

Stok pili doğru gizlemek tek başına yeterli ürün davranışı olmayabilir: kullanıcı iki pili de görmek
isteyebilir. Böyle bir birlikte gösterim desteklenecekse negatif ring margin'inin önceki görünür
pilin çizimine girmesine izin verilmemeli. Mevcut `edgeInsetPx` hesabı:

```text
reserved = floor(side * edgePadding / 100)
inset = reserved - side
side=70, edgePadding=78 => reserved=54, inset=-16
```

İlgili yerler:

- `hook/SlotGeometry.kt:edgeInsetPx`
- `hook/DuoIconHost.kt:layoutParamsFor`, `ringInset`, `assignBox`, `place`
- Overlay yolu: `anchorOnBattery`; split/locked yolda spacer ve `iconSlotInset` da var.

Basit geçici ayar: **Edge spacing=100%, position=0 dp**. Bu hesapta negatif başlangıç margin'i
kalkar. Yeni KSpeed çakışmasının çözümü için bu ayarı zorunlu kılmadık. İki pilin yan yana durması için
kalıcı çözüm, önceki komşunun gerçek çizim alanına göre ring'e yeterli flow alanı ayırmak veya açık bir
birlikte gösterim kipi sunmaktır. Mevcut kullanıcıların varsayılan geometrisini keyfi olarak değiştirme.

Duo tarafında uygulama için testler: klasik pil / ölçülen Compose pil / pil olmayan ComposeView;
show-stock kapalı ve açık; ring %100 ve %120; edge 0/78/100; görünür sistem simgesi yok/DND/data saver;
split/locked/overlay/shade/keyguard; RTL. İlgili yüzeylerin önce/sonra tanı dökümlerini karşılaştır.

## Minimum Android ve Monet planı

KSpeed `app/build.gradle`: **minSdk 31 = Android 12**; compileSdk/targetSdk 36 = Android 16.
Target ve minimum farklıdır; target 36 uygulamanın sadece Android 16'ya kurulacağı anlamına gelmez.
Duo'nun kendi minimumu ise `app/build.gradle.kts` içinde **minSdk 33 = Android 13**.
Sadece Gradle minimumu değiştirerek eski Android/başka ROM SystemUI uyumu kazanılmaz.

**Monet mümkün; minimumu artırmak gerekmiyor.** Android'in resmi Dynamic Color desteği Android 12'de
başlıyor: [Android Developers / Dynamic Colors](https://developer.android.com/develop/ui/views/theming/dynamic-colors).
Java/View tabanında Material 3 tema ve `DynamicColors` ile uygulanabilir; Compose zorunlu değil.
Compose seçilirse UI bağımlılıklarını ayarlar uygulamasıyla sınırla; SystemUI'ye enjekte edilen hız
view'ini Material/Compose bileşenlerine dönüştürme.

### Öncelikli açık hata: ayarlar metinleri durum çubuğuna taşıyor

Kullanıcı bunu KSpeed Network kurulduktan sonra da açıkça bildirdi. Ayarlar uygulamasındaki
`General` kategori başlığı ve gösterge anahtarının
`Show the network speed indicator in the status bar` açıklaması üst sistem durum çubuğuna kayıyor.
Saat, şarj/hız ve sistem simgelerinin bulunduğu alan ile uygulamanın ayar metinleri üst üste geliyor.
Eski görseller `1436.png` ve `1437.png` bu yerleşim sorununu metinsel olarak da örnekliyor. Sorun,
SystemUI'ye eklenen hız view'inden bağımsız; **uygulamanın kendi ayarlar Activity'sinin** yerleşimi.

İncelenecek kaynaklar:

- `app/src/main/java/io/github/kadirjohn/kspeed/SettingsActivity.java:onCreate`: fragment doğrudan
  `android.R.id.content` içine ekleniyor; kaynakta açık bir WindowInsets uygulaması yok.
- `app/src/main/res/values/themes.xml`: mevcut `Theme.AppCompat.DayNight.DarkActionBar`.
- Manifest ve `app/build.gradle`: targetSdk 36; edge-to-edge/decor davranışı bu hedef ve cihazın
  Android 17 sürümüyle birlikte incelenmeli. Henüz tek bir framework ayarının kök neden olduğu ayrıca
  ölçülmedi; belirti kesin, düzeltme sonraki ajana bırakıldı.

Uygulama gereksinimleri:

- Sistem status/navigation bar ve display-cutout inset'lerini gerçek WindowInsets verisinden al;
  sabit `24dp` üst boşluk veya yalnız bu Xiaomi'ye uyan koordinat kullanma.
- Üst app bar ile kaydırılabilir ayar listesinin inset sahipliğini açık kur. AppCompat/Material zaten
  bir inset uyguluyorsa aynı boşluğu ikinci kez ekleme; yeniden layout/rotation sırasında padding
  biriktirme. Uygulama kökünü ve toolbar/listenin sınırlarını ölç.
- Scroll sırasında liste app bar ile doğru kırpılmalı; kategori başlıkları, açıklamalar ve switch
  dokunma alanları sistemin saat/simge alanına çizilmemeli veya taşmamalı.
- Alt satırlar da gesture/üç düğmeli navigation bar arkasında kalmamalı; hex girişindeki klavye
  açılınca düzenleme alanı görünür ve erişilebilir olmalı.
- Bu düzeltme yalnız Monet renk değişikliği olarak raporlanmamalı; ayrı bir davranış düzeltmesi
  olarak commit açıklamasında ve teslim özetinde belirtilmeli.

Kabul kontrolü: ilk açılışta ve liste en üste kaydırıldığında `General`, gösterge anahtarı ve açıklaması
tam görünür; hiçbirinin pikseli sistemin saat/simge bölgesine girmez. Listenin sonuna kaydırıldığında
son ayar erişilebilir. Portrait/landscape, büyük sistem yazısı, açık/koyu tema, ekran kesiti ve yeniden
açma durumlarında üst boşluk doğru kalır. Mümkünse API31 ve ölçülen API37 cihazda doğrula; yalnız test
edilen sürümler için canlı doğrulama iddiasında bulun.

Önerilen iş sırası:

1. Uygulama temasını Material 3 DayNight yap; cihaz destekliyorsa wallpaper tabanlı dinamik renk,
   desteklemiyorsa okunaklı sabit açık/koyu palet kullan.
2. Üst başlık ve liste için status/navigation bar insets uygula. Görsellerde kategori başlıkları üst
   çubuğun arkasına çıkıyor; yalnız tema rengi değiştirmek bu yerleşim sorununu çözmez.
3. Ana aç/kapat kontrolü; Genel, Gösterim, Görünüm, Nerede gösterilsin, Hakkında grupları. Telefonda
   okunaklı başlık/satır yüksekliği ve klavyeyle/touch ile erişilebilir kontroller.
4. ListPreference seçeneklerini Material seçim dialog/sheet; switch ve slider'ları uygun Material
   karşılıklarıyla yenile. 0.5/1/2/3 saniye ve trafik/birim seçeneklerinin tamamını koru.
5. İki yön için ayrı renk seçici eklenebilir ama hex girişi, ayrı değerler ve auto/custom davranışı
   korunmalı. Renkler doğrudan rastgele pembe yapılmamalı; ekran görüntüsünün pembe görünümü mevcut
   duvar kağıdı paletinin sonucudur.
6. Tercih deposu ve provider sözleşmesi aynı kalsın; kontrol başlıklarını değiştirmek veri anahtarını
   değiştirmeyi gerektirmez. Düzenleme sonrası `notifyChange` korunmalı.
7. Açık/koyu tema, farklı duvar kağıdı, büyük font, küçük ekran, landscape ve klavye açık durumunu
   cihazda kontrol et. Yeni tasarım, hız göstergesinin mevcut unit/renk/satır seçeneklerini etkilememeli.

### Monet işine eklenecek zorunlu ikon yenilemesi

Kullanıcının ek talebi: güzel, güncel, Android'e native görünen; uygulamanın kendi çizdiği **beyaz
arkaplan/plaka içermeyen** ve **monochrome themed icons** destekleyen yeni launcher ikonu. Yukarıdaki
iki Neoteric Launcher görüntüsü, hem normal hem dual-tone karşılaştırmasının görsel şartnamesidir.
Bu bölüm sonraki ajana bırakılmıştır; mevcut K/ok çizimi nihai ikon olarak kabul edilmemeli.

Mevcut manifest `android:icon="@drawable/ic_launcher"` kullanıyor. Bu resource, arkaplan dikdörtgeni
ve işareti tek vector içinde çiziyor. Adaptive icon katmanları ve `monochrome` resource'u yok.
Dolayısıyla sorun sadece beyaz bir `fillColor` değiştirmekle çözülemez: kaynakta zaten koyu arkaplan
var; görülen dış açık plaka, launcher'ın eski tip ikonu sarmalaması/temalandırmasıyla uyumludur.
Launcher'ın iç algoritması ayrıca incelenmedi; kesin olan mevcut adaptive katmanların eksikliğidir.

İş listesi:

- Foreground yalnız okunaklı bir K/network işareti olsun; opak kare, dış maske ve beyaz plaka çizmesin.
  Küçük boyutta ince detay veya minik ayrı ok yığını kullanma. Mevcut K/ok fikri yeniden tasarlanabilir.
- Normal sürüm için ayrı, maskeyi dolduran bilinçli bir marka arkaplanı seç; işareti beyaz bir karenin
  içine oturtma. Koyu yüzey + açık işaret, kullanıcının komşu ikon örnekleriyle uyumlu bir başlangıçtır.
- `res/drawable/ic_launcher_foreground.xml`, ayrı monochrome siluet drawable'ı ve arkaplan rengi;
  `res/mipmap-anydpi-v26/ic_launcher.xml` adaptive tanımı; API33 için gerekirse `-v33` varyantında
  `<monochrome>`; manifestte `@mipmap/ic_launcher`. Round resource kullanılacaksa aynı işareti koru.
- Monochrome maskesine arkaplan karesi katma; alpha yalnız işaretin siluetini tanımlasın. Renkleri
  launcher uygulasın. Normal ve monochrome siluetleri görsel ağırlık/ölçek olarak aynı ailede dursun.
- Android'in katman ölçüsü 108×108 dp, korunmuş merkezi alan 66×66 dp; önemli işaret bu alanda kalsın.
  Native temalı ikon desteği API33'ten başlar; API31–32'de normal adaptive ikon çalışmaya devam eder.
  Bkz. [resmi adaptive icon şartları](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
- Neoteric Launcher'da normal ve dual-tone modlarını, açık/koyu temayı ve daire/squircle maskelerini
  yeniden kurulumdan sonra kontrol et. Launcher önbelleği eski ikonu gösteriyorsa yenile. Beyaz iç
  plaka, iç içe kare, kesilmiş kenar ve komşulardan belirgin küçük işaret kabul edilmez.

Themed modun nihai dış yüzey rengini launcher/tema belirler. Ürün gereksinimi uygulamanın kendi
beyaz plakasını ve gereksiz iç karesini kaldırmak; sistemin tüm temalarda dış yüzeyi koyu tutacağını
varsaymak değil. Ayarlar ekranına Monet eklemek launcher ikonunu kendiliğinden düzeltmez; iki iş de
ayrı uygulanıp görsel olarak doğrulanmalı. Bu iş minimum Android sürümünü artırmayı gerektirmez.

Korunacak anahtar ve türler:

| Anahtar | Tür | Kod varsayılanı |
| --- | --- | --- |
| `enabled` | boolean | true |
| `mode` | String | both |
| `interval_ms` | String | 1000 |
| `source` | String | total |
| `unit` | String | auto_bytes |
| `show_unit` | boolean | true |
| `up_on_top` | boolean | false |
| `dim_zero` | boolean | true |
| `hide_idle` | boolean | false |
| `idle_threshold_kb` | int | 1 |
| `text_size_sp` | int | 8 |
| `color_mode` | String | auto |
| `custom_color` | String | #FFFFFF |
| `custom_color_ul` | String | #FFFFFF |
| `show_on_shade` | boolean | false |
| `show_on_lockscreen` | boolean | false |

Örneğin cihazda `up_on_top=true`, `hide_idle=true`, eşik=100; bunlar kod varsayılanından farklı,
kullanıcının seçimidir. UI yeniden yazılırken defaults uygulanarak üzerlerine yazılmamalı.
`netspeed_app` içindeki son güncelleme zamanı ise uygulama içi metadatadır, gösterge ayarı değildir.

## Kontroller ve kalan sınırlar

Çalıştırılan kontrol:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

5 test: görünür sistem simgesi olmayan ring taşması ve margin kararlılığı; simgelerin gidip gelmesi;
translation ve ring kaldırılması; RTL; kısa/uzun hız satırlarının ok/metin aralığı ve sağ hizası.
Lint'te 0 hata, 6 uyarı: dependency/build-tool güncelliği, redundant label, dinamik resource lookup,
exported settings provider ve backup ayarı. Tüm ROM'larda veya API31'de canlı test yapıldığı iddia edilmez.

Kullanıcının cihazda etkinleştirmesi ve son görüntüsüyle ana durum çubuğu doğrulandı. Shade/lockscreen
seçenekleri cihazda kapalıydı; bu iki yüzey ve başka ROM'lar ayrıca cihaz kontrolü ister.
Yeni APK `app/build/outputs/apk/debug/app-debug.apk`. Yeni release yayınlanmadı. Ayrı Duo değişiklikleri,
ayarlar ekranının sistem çubuğu taşması, tam Monet ekranı ve adaptive/monochrome ikon yenilemesi
sonraki ajana bırakılan işlerdir.

## Commit ve uzak depo teslim sözleşmesi

Kullanıcı yapılanların yalnız yerelde kalmamasını ve kendi kimliğiyle kaydedilmesini istiyor.
Bu çalışma için mevcut Git kimliği `kadirjohn <kc4ca@protonmail.com>`; uzak depo
`https://github.com/kadirjohn/kspeed-network-lsposed.git`, teslim dalı `main`.
Bu repo dışındaki Duo değişiklikleri kendi deposunda, kendi erişim/çalışma kurallarıyla ele alınmalı.

Sonraki ajan her commit'te anlamlı değişikliklerin tamamını açıklamalı: hangi hatanın giderildiği,
son davranış, değişen ayar/ikon/paket veya provider sözleşmesi, yapılan cihaz/otomatik kontroller ve
doğrulanmamış noktalar. Yalnız `UI fixes` gibi genel bir açıklama kullanmamalı. Kullanıcıya teslimde
commit kimliği, dal, tıklanabilir GitHub commit bağlantısı ve push'ın başarılı olup olmadığı verilmeli.
Yapılmayan iş yapılmış gibi yazılmamalı; test edilmemiş yüzeyler açıkça belirtilmeli.

Bu oturumun commit kapsamı: NetSpeed'in sistem simgelerinden önce yerleşmesi; negatif margin/translation
koruması ve RTL; yakın ok/metin satırları; paket/provider/Xposed/ProGuard/isim/kaynak-güncelleme bağlantısı
rebrand'i; geçici K/ok vector ikonu; sürüm 1.0.3-dev; 5 yerleşim testi ve test yapılandırması; README,
görsellerin metin karşılıkları, cihaz koordinat kanıtları ve bu devir belgesi. Ayarlar ekranı taşması,
tam Monet tasarımı, nihai adaptive/monochrome ikon ve Duo kodu bu commit'te düzeltilmiş sayılmaz.
