# Kilit ekranında gösterge görünmüyor (Pixel 9) — açık iş

Tarih: 2026-11-05 (devir notu; sorun 2026-11-04 gecesi yaşandı)
Durum: **AÇIK — devam edilecek.** Kullanıcı talimatı: bu kısım bu şekilde kalacak, kesinlikle devam edilecek.

## Yaşanan sorun

Pixel 9 (Android 17, `tokay`, Compose tabanlı SystemUI) üzerinde `show_on_lockscreen=true`
olsa bile göstergenin kilit ekranında görünmediği kullanıcı tarafından tekrarlanarak bildirildi.

## Çelişen kanıtlar (dürüst envanter)

- **ADB `screencap` kanıtı:** Kilitli ekran görüntüsünde gösterge DOĞRU konumda göründü
  (sağ üstte, pil `%96`'nın solunda, sarı `↑ 67 KB/s` + yeşil `↓ 11 KB/s`; solda
  `Vodafone TR` + saat). `2026-11-04 01:39` karesi: sarı pikseller x740-840 bandında ölçüldü.
- **Kullanının gözü:** Aynı zaman diliminde kullanıcı kilit ekranında göstergeyi
  **göremedi** — iki kez teyit edildi.
- Olası açıklama: `screencap` view katmanını yakalarken cihazın gerçek kilit sahnesi
  (Compose) farklı bir kompozisyon gösterebilir; ya da AOD/geçiş karesi yakalandı.
  Bu çelişki **çözülmeden** bırakıldı — kanıt olarak ekran görüntüsüne güvenilmesi
  önerilmez.

## Yapılan düzeltmeler (bu soruna yönelik, hepsi ana dalda)

1. **Koşulsuz tick başlangıcı** (`NetSpeedView.onAttachedToWindow`): Bu ROMda yüzey
   pencereleri attach'te `windowVis=0` bildiriyor ve sonrasında
   `onWindowVisibilityChanged(VISIBLE)` hiç gelmiyordu → döngü hiç başlamıyordu.
   Loop artık attach'te koşulsuz başlıyor. Bu düzeltme shade/collapsed yüzeylerinde
   ölçülebilir iyileşme sağladı (`attach surface=...` logları artık geliyor).
2. **Kalıcı görünürlük logları** (`setIndicatorVisibility` → `XposedBridge.log`):
   her yüzeyin VISIBLE/GONE geçişleri, `cfgKeys`, `showLock` değerleriyle LSPosed
   modül loguna yazılıyor. Teşhis için kalsın; Xiaomi doğrulamasından sonra
   temizlenecek (diğer insert loglarıyla birlikte — kullanıcı talimatı).

## Teşhis sırasında elenmiş/elenmemiş hipotezler

- `keyguard_header` ağacına insert **yapılıyor** (log: `inserted on 'keyguard' at index 0`,
  ancestry: `statusIcons ← system_icons ← system_icons_container ← status_icon_area ←
  KeyguardStatusBarView#keyguard_header` — klasik zincir, Compose interop YOK).
- Keyguard view'ında visibility **geçişi hiç loglanmadı** → ya update çalışıp
  görünürlük değişmedi (başlangıç VISIBLE ise `changed=false`) ya da tick çalışmıyor.
  **Ayrıştırılamadı.**
- Config akışı çalışıyor (hello beacon geliyor, provider erişimi sağlam).
- **Elenmeyen ana şüphe:** Bu ROMun kilit ekranı Compose sahnesi (`SceneFramework`,
  `WindowManagerLsVis` logları görüldü) klasik `KeyguardStatusBarView`'ı **üstten
  kapatıyor** olabilir; view render edilir ama sahneye ulaşmaz.

## Devam edilecek adımlar (öneri sırası)

1. Kilitliyken **shade'i PIN girmeden yarım açma** testi: `keyguard_header` o anda tam
   görünür olur. Göstergenin orada çıkması "render var ama kilit sahnesi kapatıyor"
   teorisini kesinleştirir. (Kullanıcıdan tek soruluk doğrulama bekleniyor.)
2. Teori kesinleşirse çözüm yönleri: kilit sahnesinin Compose katmanına müdahale yerine
   `keyguard_header`'ı öne çıkarma denemesi (elevation/translationX testleri — riskli,
   ROM'a bağlı), ya da kilit yüzeyi için ROM'un kendi kilit saat bölgesine alternatif
   anchor arayışı.
3. `log.tag.KSpeed DEBUG` prop'u SystemUI'de `isLoggable` cache'i yüzünden etkin
   olamıyor; kalıcı `XposedBridge.log` zaten eklendi — teşhis bundan devam etsin.
4. Xiaomi (referans cihaz) canlı testi hâlâ yapılmadı — yeni insert mantığı
   (`noVisibleSiblingAfter` kaçışı) orada doğrulanmalı.

## İlgili dosyalar

- `app/src/main/java/io/github/kadirjohn/kspeed/NetSpeedView.java` (loop, visibility logları)
- `app/src/main/java/io/github/kadirjohn/kspeed/StatusBarHook.java` (insert, sweep, relocate)
- Kanıt logları: cihazda `/data/adb/lspd/log/modules_*.log` (`[KSpeed]` etiketli satırlar)
