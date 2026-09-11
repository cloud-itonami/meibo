(ns meibo.methods.verification-window
  "meibo 名簿 — verification-window registry (ADR-2607062200 scope 拡張, 2026-07-28).

  「Xを名乗る連絡があった」ときに、その名乗りを一切評価せず **X自身が公表して
  いる窓口** を引き当てるための表。既存 directory.cljc と同じく機関レベルのみ
  (G1)・非裁定(G2)・出所正直(G10)。

  本ウェーブで追加された不変条件:

  **G11 no-inbound-attestation** — この名前空間は着信側の番号を入力に取る関数を
  一切持たない。発信者番号は偽装できるため、既知の正規番号との一致を根拠に
  『本物』を返す API は安全側ではなく**危険側**に倒れる(一致しなければ偽物、
  一致すれば本物、という二重の誤りを両方生む)。したがって出力は常に
  `:call-the-published-window` の一方向のみで、`:caller-is-genuine` に相当する
  戻り値は存在しない。test_verification_window.cljc がこの不在を強制する。

  なぜこれが効くのか(system-dynamics 側の根拠): 判断の質を上げる対策は、判断そのもの
  が攻撃対象になっている状況では効かない。実測(はてな事案 2026-04-20)では銀行の異常
  検知が送金ウィンドウの18.5%時点で発火していたにもかかわらず、その出力の終端先が
  攻撃を受けている本人だったため実効ゲインが0になった。この表の役割は判断を良くする
  ことではなく、**判断を引き当てに置き換える**こと。詳細は
  kotoba-lang/loop-system-dynamics の corporate-vishing-fraud サイクル。

  House style: ':…' キーワードは文字列のまま(edn.cljc の忠実性不変条件)。pure fns;
  I/O は #?(:clj) の縁でのみ。"
  (:require [meibo.methods.edn :as edn]
            [meibo.methods.directory :as dir]))

#?(:clj
   (defn load-windows
     ([] (load-windows (clojure.java.io/file (edn/here) "data" "verification-window.edn")))
     ([path] (->> (edn/load-edn path) (filter #(contains? % ":vw/id")) vec))))

(defn- window [w]
  {"id" (get w ":vw/id")
   "jurisdiction" (get w ":vw/jurisdiction")
   "institution_class" (get w ":vw/institution-class")
   "institution" (get w ":vw/institution")
   "label" (get w ":vw/label")
   "url" (get w ":vw/url")
   ;; nil when the institution does not publish the number on :vw/url itself.
   ;; Deliberately NOT backfilled from another source (G10).
   "published_number" (get w ":vw/published-number")
   "number_source" (get w ":vw/number-source")
   "covers" (vec (get w ":vw/covers"))
   "verified_at" (get w ":vw/verified-at")
   "verified_method" (get w ":vw/verified-method")
   "verified_via" (get w ":vw/verified-via")
   "note" (get w ":vw/note")})

;; ---------------------------------------------------------------------------
;; claim taxonomy
;; ---------------------------------------------------------------------------

(def claim->directory-kind
  "Claims already answered by the EXISTING legal-directory registry, so this
  wave does not duplicate them. `:claim/court` is covered by the 10-jurisdiction
  `:court-locator` entries; `:claim/lawyer` by `:bar-association`. Adding a
  second copy here would create two registries that can silently disagree."
  {":claim/court" ":court-locator"
   ":claim/lawyer" ":bar-association"})

(def claim-worklist
  "Impersonated-authority claims this registry does NOT yet answer, named
  explicitly rather than silently absent (G10 — the same discipline
  coverage_report.cljc applies to jurisdictions). Entries drop off as they are
  covered."
  [":claim/securities-firm"        ;; 日本証券業協会 / FINMAC — 窓口URLが本ウェーブで404、推測しない
   ":claim/tax-authority"          ;; 国税庁・税務署を名乗る手口
   ":claim/prosecutor"             ;; 検察庁を名乗る手口
   ":claim/telecom-carrier"        ;; 通信事業者を名乗り「不正契約がある」と言う手口
   ":claim/cert-coordination"      ;; JPCERT/CC
   ":claim/court-bailiff"])        ;; 執行官・裁判所書記官を名乗る手口

(defn claims-covered
  ([] (claims-covered (load-windows)))
  ([windows]
   (vec (sort (distinct (mapcat #(get % ":vw/covers") windows))))))

(defn institution-classes
  ([] (institution-classes (load-windows)))
  ([windows]
   (vec (sort (distinct (map #(get % ":vw/institution-class") windows))))))

;; ---------------------------------------------------------------------------
;; lookup — outbound only (G11)
;; ---------------------------------------------------------------------------

(defn windows-for-claim
  "Every published window that can verify `claim` in `juris`. Empty vector for
  an uncovered claim — honest degrade, never a guessed window (G10)."
  ([claim juris] (windows-for-claim claim juris (load-windows)))
  ([claim juris windows]
   (->> windows
        (filterv (fn [w] (and (= (get w ":vw/jurisdiction") juris)
                              (some #{claim} (get w ":vw/covers")))))
        (mapv window))))

(defn lookup
  "The ONLY consumer-facing entry point.

  Takes the claim someone MADE (\":claim/police\") and a jurisdiction. Never
  takes the inbound number, the caller-id, the display name, or any other
  attacker-controlled input — G11.

  The verdict is a constant. `:call-the-published-window` is returned whether
  or not a window is known, because 'we have no window for this claim' is not
  evidence the caller is genuine. There is no code path that returns anything
  resembling 'the caller is legitimate'.

  When the claim is one the EXISTING legal-directory already answers (court,
  lawyer), this delegates there instead of duplicating the data."
  ([claim juris] (lookup claim juris (load-windows) (dir/load-directory)))
  ([claim juris windows entries]
   (let [delegated-kind (get claim->directory-kind claim)
         ws (if delegated-kind
              (filterv #(= (get % "kind") delegated-kind) (dir/by-jurisdiction juris entries))
              (windows-for-claim claim juris windows))]
     {"claim" claim
      "jurisdiction" juris
      ;; constant, by construction — see G11
      "verdict" ":call-the-published-window"
      "delegated_to_legal_directory" (some? delegated-kind)
      "windows" (vec ws)
      "covered" (boolean (seq ws))
      "guidance"
      (if (seq ws)
        "着信の名乗り・発信者番号・折り返し先として相手が伝えた番号は一切使わない。上記の機関自身が公表する窓口へ、自分から新規発信して確認する。"
        "この名乗りに対応する検証窓口は未収載。相手が伝えた番号は使わず、機関の公式サイトを自分で開いて公表窓口を確認する。未収載であることは相手が本物である根拠にはならない。")
      "worklist" (vec claim-worklist)})))

;; ---------------------------------------------------------------------------
;; coverage
;; ---------------------------------------------------------------------------

(defn coverage
  ([] (coverage (load-windows)))
  ([windows]
   (let [claims (claims-covered windows)]
     {"windows_total" (count windows)
      "jurisdictions" (vec (sort (distinct (map #(get % ":vw/jurisdiction") windows))))
      "institution_classes" (institution-classes windows)
      "claims_covered" claims
      "claims_delegated_to_legal_directory" (vec (sort (keys claim->directory-kind)))
      "claims_worklist" (vec claim-worklist)
      ;; how many entries publish the number on their own page vs point at it
      "numbers_on_page" (count (filterv #(= (get % ":vw/number-source") ":on-page") windows))
      "numbers_not_published_here"
      (count (filterv #(= (get % ":vw/number-source") ":not-published-here") windows))
      "directly_fetched"
      (count (filterv #(= (get % ":vw/verified-method") ":webfetch") windows))
      "linked_from_verified_page"
      (count (filterv #(= (get % ":vw/verified-method") ":linked-from-verified-page") windows))})))
