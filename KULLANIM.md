# Simple Radio – Genişletilmiş Sürüm (4.5.7.9-ham)

Bu sürüm, CodinGlitch'in **Simple Radio** (Simple Voice Chat eklentisi) modunun Forge 1.20.1 için
düzenlenmiş halidir. Orijinal mod GPLv3 lisanslıdır; bu değiştirilmiş sürüm de GPLv3'tür.

Oyun içinde her şey **Operatör Kılavuzu** eşyasında (Türkçe/İngilizce) anlatılır. Her oyuncu sunucuya
ilk girişinde bir kılavuz alır; tarifi: kitap + bakır tel.

## Gerekenler

| Mod | Durum |
|---|---|
| Minecraft 1.20.1 + Forge 47.x | zorunlu |
| Simple Voice Chat (forge-1.20.1-2.6.x) | zorunlu |
| Lexiconfig 1.4.x (Forge) | zorunlu |
| Electrodynamics 1.20.1 + Voltaic | isteğe bağlı (yoksa cihazlar Forge Energy / FE ile beslenir) |

Orijinal `simpleradio-forge-1.20.1-4.5.7.9.jar` dosyasını **kaldırıp** yerine bu jar'ı koyun
(ikisi aynı anda yüklenemez, mod kimliği aynıdır). Sunucu ve tüm oyuncular aynı jar'ı kullanmalıdır.
Sunucunun ayarları (batarya, bant planı, güç...) oyuncu girişinde istemciye gönderilir; ekranlar ve
ipuçları sunucunun kurallarını gösterir.

---

## 1. Lisans, çağrı işareti ve bant planı

* **Yayın için lisans gerekmez.** Lisans isteğe bağlıdır ve yalnızca çağrı işareti verir
  (sunucu isterse `band_plan.requireLicense = true` ile zorunlu yapabilir).
* **Lisans Sınav Kitapçığı** (2 kağıt + mürekkep kesesi + bakır tel) ile sınava gir: 10 soru, 7 doğru
  ile geçilir; kalınca 5 dakika sonra tekrar girilebilir. Sorular kılavuzdaki konulardan gelir.
* Geçince **TA3KQ** gibi bir çağrı işareti alırsın. APRS bunu gönderir (lisans yoksa oyuncu adını).
* **Dinlemek her zaman serbest.** Yalnızca bant planı nerede yayın yapılabileceğini sınırlar.
* Amatör telsizler (el telsizi, istasyon, araç telsizi, APRS) yalnızca amatör bantlarda yayın yapar:
  160 m 1,810–2,000 · 80 m 3,500–3,800 · 40 m 7,000–7,200 · 30 m 10,100–10,150 · 20 m 14,000–14,350 ·
  17 m 18,068–18,168 · 15 m 21,000–21,450 · 12 m 24,890–24,990 · 10 m 28,000–29,700 · 6 m 50–52 ·
  2 m 144–146 · 70 cm 430–440 MHz.
* **El Telsizi / Patates Telsiz = PMR446:** lisanssız, 446,00625 MHz'den 12,5 kHz arayla 16 kanal.
* Komutlar: `/telsiz lisans` (herkes), `/telsiz yayilim` (herkes, HF koşulları),
  `/telsiz lisans ver|iptal <oyuncu>` ve `/telsiz cagri <oyuncu> <çağrı>` (operatör).
* Bant planını kapatmak için: `band_plan.enforce = false`.

## 2. El telsizleri (Alıcı-Verici, APRS, El Telsizi, Patates Telsiz)

* **Kullan tuşunu basılı tut = konuş**, bırak = dinle (yarı dubleks). **Eğil + kullan** = ayar ekranı.
* Ayarlar: RX frekansı, FM/AM/SSB, shift (simpleks, ±0,6, ±7,6 MHz), aç/kapa, **CTCSS** gönderme tonu ve
  alış ton susturucusu.
* **16 hafıza kanalı** (isimli): ayar ekranında Kaydet / Çağır / Sil; telsiz elindeyken
  **PageUp / PageDown** ile kanal değiştir.
* **Çift dinleme:** ikinci bir frekansı da dinler; ana frekans önceliklidir, yayın hep ana frekanstan.
* **Tarama:** kayıtlı kanalları (en az 2) saniyede 10 kanal gezer, dolu kanalda durur.
* **Gösterge (HUD):** sıcak çubuğun yanında frekans, kanal, S-metre, TX, batarya, tarama.
* **Batarya:** dinlemede 4 Minecraft günü; yayında 4 kat hızlı biter. Yarının altında güç düşer, %10'un
  altında bip sesi. Şarj istasyonunda şarj edilir veya dolu **batarya paketiyle** craft tablasında
  değiştirilir (tablada kalan paket telsizin eski şarjını taşır).

## 3. Sabit İstasyon

* HF / VHF / UHF, **1–100 W**, FM/AM/**SSB**. Sağ tık → panel: bant, frekans, mod, shift, güç, CTCSS,
  **RIT** (±990 Hz), **VOX**, Yagi yönü, S-metre, SWR, sıcaklık, güneş akısı / K indeksi / bant durumu,
  QRM.
* **Konuşmak:**
  * İstasyonun ~4 blok yakınında **B** (İstasyon Bas-Konuş).
  * **Masa mikrofonu:** istasyonun 4 blok yakınına koy, ona bakarak kullan tuşunu basılı tut (6 bloğa kadar).
  * **El mikrofonu:** istasyona kullanarak bağla, sonra ~8 blok içinde kullan tuşunu basılı tut.
  * **VOX** açıksa istasyonun yanında konuşmak yayını açar.
* **SSB:** FM'den 6 dB daha iyi geçer. Karşı taraf kaymışsa ses ince/kalın gelir → RIT'i çevir.
* **S-metre:** 1 S birimi = 4 dB (S1–S9 yeşil, S9+10/+20/+30 kırmızı). İstasyon, röle ve tarayıcı
  panellerinde, el telsizi göstergesinde ve **orijinal Radyo / Alıcı bloklarında** vardır: bu bloklara
  8 blok içinden bakınca nişangahın yanında frekans ve S-metre çıkar; blok çalışmıyorsa nedeni yazar
  (güç yok / katalizör yok). İstemci ayarı: `hud.blockSMeter`.
* **Isınma:** güç katı yayında ısınır (kötü antende çok hızlı). %100'de kapanır, %60'ta geri açılır.
* **Yıldırım:** en az 6 blok yüksek anten fırtınada yıldırım çeker. Anten tepesinin 3 blok yakınına
  **paratoner** koy; telsize değen bir paratoner topraklama yapar. Yanan istasyon/röle/tarayıcı
  **4 kızıltaşla** sağ tıklanarak onarılır.
* **Kırınca ayarlar korunur:** istasyon, röle, tarayıcı ve tilki vericisi kırıldığında frekans ve
  ayarlarını eşyada saklar (ipucunda görünür), koyunca geri yükler. Kimlik, enerji ve ısı saklanmaz.

## 4. Antenler, SWR ve anten tuner'ı

* **Direk:** istasyonun üstüne anten direği blokları diz. VHF/UHF'de iyidir; HF'de çeyrek dalga boyuna
  yakın olmalıdır (14 MHz ≈ 5 blok, 7 MHz ≈ 10 blok, 3,5 MHz ≈ 21 blok).
* **Yagi:** direğin tepesine; ~12 dBi ileri, arkası zayıf. Boş elle sağ tık / eğilip sağ tık ya da
  panelden döndürülür.
* **Çift bant dikey anten + koaksiyel kablo:** önce antene, sonra istasyona/röleye kabloyla sağ tık
  (8 blok başına 1 kablo, en fazla 48 blok). Kablo kaybı: UHF 9 dB, VHF 5 dB, HF 1,5 dB / 100 blok.
* **Antensiz istasyon neredeyse hiç yayın yapamaz.**
* **SWR:** uyumsuz anten gücü geri yansıtır: panel yüksek SWR gösterir, istasyon gücü düşürür ve hızla
  ısınır. SWR 3'ün üstünde ciddi kayıp olur.
* **Anten tuner'ı:** istasyona değecek şekilde koy; SWR 10'a kadar olan antenleri 1,1'e eşler (kısa
  direk HF'de de çalışır, biraz daha az verimle).

## 5. Güç (13,8 V, Electrodynamics / FE)

* İstasyon, röle, tarayıcı ve tilki vericisi **13,8 V DC** ile çalışır.
  * **Electrodynamics ile:** şebeke gerilimi (120 V) doğrudan verilirse cihaz kabul etmez; şebekenin çok
    üstünde gerilimde patlar. Araya **13,8 V Güç Kaynağı** koy.
  * **Forge Energy (FE):** gerilim kavramı yoktur, FE ile doğrudan beslenebilir.
* **Güç kaynağı:** telsizin bir yüzüne tıklayarak koy (DC çıkışı telsize bakar); şebekeyi/FE'yi diğer
  yüzlerden ver. Güç verirken göstergeleri yanar. Verim %90.
* **13,8 V hattı:** güç kaynağının çıkışına Electrodynamics kablosu bağlarsan o kablo ağındaki her şey
  13,8 V alır. Tek güç kaynağıyla istasyon, röle, tarayıcı, tilki vericisi, şarj istasyonu ve orijinal
  telsiz bloklarının hepsi beslenebilir. (Bu hatta şebeke gerilimi verme: 13,8 V cihazlar onu reddeder.)
* Orijinal Simple Radio blokları (radyo, verici, alıcı, hoparlör) ve şarj istasyonu da elektrik ister
  (`power.requirePower`); hem şebekeyle (120 V) hem 13,8 V hattıyla çalışırlar.
* **Yüksek güç paraziti:** 50 W ve üzeri yayın, antenin çevresindeki çıplak Electrodynamics kablo
  ağlarında elektriği keser (yalıtımlılar etkilenmez).
* **Kömür jeneratörü** (5 demir külçe, 2 kızıltaş, ocak ve bakır külçe): kömür, odun kömürü ya da kömür
  bloğu yakar (64'e kadar alır), yanarken tikte 40 FE üretir, 20 000 FE saklar. Deposu doluyken yakmaz,
  boşta soğuk bekler. Kullanarak kömür koyulur.
* **Kablolu bloklar:** soyulmuş ladin kütüğü, ladin tahtası ve taş tuğla, içinden ince bir kablo geçen
  halleriyle (blok + bakır külçe). Birbirine değen kablolu bloklar jeneratörün gücünü hattın ucundaki
  telsizlere taşır: bir gözetleme kulesinin bacağından yukarı, döşemenin altından istasyona.

## 6. Röle (Repeater)

* Girişi (çıkış + shift) dinler, çıkıştan kendi gücüyle tekrar yayınlar. Örnek: çıkış 438,650,
  shift -7,600 → el telsizleri RX 438,650, shift -7,6.
* **Erişim tonu** (CTCSS) ve **çıkış tonu**, **kuyruk sesi** (BEEP / K / NONE).
* **Zaman aşımı:** tek yayın 180 saniyede kesilir ("bonk").
* **Mors kimliği:** panelde yazılan çağrı işaretini her 10 dakikalık kullanımda, bir yayının sonunda
  20 WPM mors ile gönderir.
* **Bağlantı:** aynı bağlantı numarasına (1–9999) sahip röleler trafiği birbirine aktarır.
* Açıkken chunk'larını yüklü tutar (3×3). Güç kaynağını bu alanın içine kur.

## 7. Yayılım (propagation)

* **VHF/UHF görüş hattıdır:** tepeler ve dağlar sinyali zayıflatır/keser (sırtı sıyıran az, dağın
  arkasında kalan çok kaybeder). Radyo ufkunun ötesinde sinyal azalır → **yükseğe çık**.
* **Binalar, çatılar, yeraltı ve mağaralar** sinyali zayıflatır; antenin üstündeki bloklar kayıp,
  açık gökyüzü avantajdır. İstasyon ve rölede bu kayıp antenin tepesine göre hesaplanır: direğin
  tepesi açıktaysa bina engel olmaz.
* **HF:** yer dalgası (sınırlı mesafe) + gök dalgası (iyonosferden yansır, çok uzağa gider ama bir
  **atlama bölgesi** bırakır).
  * 80 m / 40 m geceleri, 15 m / 10 m gündüz ve güneş akısı yüksekken açılır.
  * Güneş akısı 27 günlük döngüyle 70–200 arasında değişir; manyetik fırtına (K indeksi) yüksek
    bantları kapatır; sporadik E bazen 6 m ve 10 m'yi açar. `/telsiz yayilim` günün durumunu gösterir.
  * Fırtınalarda HF'de parazit çatırtıları olur.
* **FM yakalama:** aynı anda iki istasyon yayın yaparsa güçlü olan duyulur, yakın güçler karışır.
* **Kayık frekans:** birkaç kHz kaymış alıcı zayıf ve bozuk duyar (SSB'de ses perdesi kayar).
* **Squelch kuyruğu:** FM yayını bitince kısa bir hışırtı duyulur.
* **QRM:** alıcının 12 blok yakınında çalışan elektrikli makineler gürültü ekler.

Açık ve düz arazide, engel yokken yaklaşık menziller (FM, aynı cihazla):

| Cihaz | Menzil |
|---|---|
| El Telsizi / Patates Telsiz (PMR) | ~2 400 blok |
| Alıcı-Verici (5 W) | ~4 600 blok |
| Araç telsizi (25 W, çatı anteni) | ~10 000 blok |
| İstasyon 100 W, çok yönlü | ~11 000 blok |
| İstasyon 100 W + Yagi (hüzme yönü) | ~40 000 blok |

## 8. Tarayıcı

* Başlangıç/bitiş frekansı, adım (5–100 kHz), FM/AM/SSB. Saniyede 200 kanal tarar, sinyalde durur,
  S-metre gösterir. **Atla** dolu kanalı bir süre pas geçer; **< >** ile elle gezilir.

## 9. Tilki avı (ARDF)

* **Tilki vericisi:** kimliğini morsla (MOE, MOI, MOS, MOH, MO5) + uzun tonla gönderir. Boş elle
  kullan → tilki numarası (1–5); el telsiziyle kullan → telsizin frekansını alır (varsayılan 144,500).
  `fox.cycleSeconds` ayarlanırsa (ör. 60) tilkiler sırayla yayın yapar; 0 = sürekli.
* **Yön bulucu:** baktığın yönden en iyi duyan alıcı (70° hüzme, 20 dB ön/arka). Kullan =
  zayıflatıcı (0/10/20/30 dB), eğil + kullan = frekans.

## 10. APRS

* **APRS El Telsizi** (alıcı-verici + pusula + harita): normal telsizdir, ayrıca konumunu 144,800 MHz'den
  90 sn'de bir (hızlıyken 20 sn) gerçek 1200 baud AX.25 paketi olarak gönderir. Çağrı işareti
  (lisans yoksa oyuncu adı) -7 ekiyle gider.
* Eğil + kullan → **APRS** düğmesi: son 30 dakikada duyulan istasyonların haritası, yayın aç/kapa, yorum.

## 11. SSTV (resim gönderme)

* **SSTV Terminali:** bir istasyonun 2 blok yakınına koy. TX yuvasına dolu harita → **Resmi gönder**
  (gerçek Robot 36, ~37 sn). Karşı tarafta MAP yuvasında boş harita olmalı: resim RX yuvasına kilitli
  bir harita olarak basılır. Zayıf sinyal karlı resim verir; sinyal kesilirse gelen kısım basılır.

## 12. Araç telsizi

* Binilen her şeyde (tekne, maden arabası, at, Create koltuğu, Immersive Aircraft...) **25 W**, çatı
  anteni ve aracın gücüyle çalışır, bataryasını şarj eder. Elde taşınınca 5 W el telsizidir.
* Kabin anteni engellemez ama tünel ve mağara engeller.

## 13. Ayarlar

Tüm değerler `simpleradio-server` yapılandırmasındadır (Lexiconfig). Yeni sayfalar: `propagation`,
`ionosphere`, `lightning`, `qrm`, `swr`, `band_plan`, `fox`, `direction_finder`, `aprs`, `mobile`,
`sstv`, `guide`; `repeater`, `station`, `handheld`, `power`, `frequency` sayfalarına yeni anahtarlar
eklendi. Her tarif ilgili sayfadaki `enabled` ile kapatılabilir (güç kaynağı: `power.psuEnabled`).
Frekans biçimi 3 ondalık (1 kHz çözünürlük, örn. `438.650`).

## Kaynak koddan derleme

```
gradlew :forge:build
```
Çıktı: `forge/build/libs/simpleradio-forge-1.20.1-4.5.7.9-ham.jar`.
Oyun içi testler: `gradlew :forge:runGameTestServer` (önce `forge/run/gametest/world` silinmeli;
Electrodynamics'siz: `-PwithoutElectrodynamics`).
