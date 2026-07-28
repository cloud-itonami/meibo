(ns meibo.tests.test-verification-window
  "meibo 名簿 — verification-window registry tests (ADR-2607062200 scope 拡張).
  clojure.test. G1/G2/G10 は既存 directory と共通、G11 は本ウェーブ固有。"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [meibo.methods.verification-window :as vw]))

(def windows (vw/load-windows))

(deftest test-registry-loads
  (is (seq windows))
  (is (= (count windows) (get (vw/coverage) "windows_total"))))

(deftest test-every-window-verified-https-url
  (doseq [w windows]
    (is (str/starts-with? (get w ":vw/url") "https://") (str (get w ":vw/id") " url"))
    (is (some? (get w ":vw/institution")))
    (is (some? (get w ":vw/label")))
    (is (some? (get w ":vw/institution-class")))
    (is (seq (get w ":vw/covers")))))

(deftest test-provenance-is-honest-g10
  ;; every entry declares WHEN and HOW it was verified; anything not directly
  ;; fetched must name the parent page it was found on, never stand alone
  (doseq [w windows]
    (is (= "2026-07-28" (get w ":vw/verified-at")))
    (is (some #{(get w ":vw/verified-method")} [":webfetch" ":linked-from-verified-page"])
        (str (get w ":vw/id") " verified-method"))
    (when (= (get w ":vw/verified-method") ":linked-from-verified-page")
      (is (some? (get w ":vw/verified-via"))
          (str (get w ":vw/id") " must name the verified page it was linked from")))))

(deftest test-number-not-backfilled-from-elsewhere-g10
  ;; a number is recorded ONLY when the institution publishes it on :vw/url
  ;; itself. :not-published-here must carry nil, never a number sourced from
  ;; somewhere else and silently attributed to this page.
  (doseq [w windows]
    (case (get w ":vw/number-source")
      ":on-page" (is (some? (get w ":vw/published-number"))
                     (str (get w ":vw/id") " claims :on-page but has no number"))
      ":not-published-here" (is (nil? (get w ":vw/published-number"))
                                (str (get w ":vw/id") " must not backfill a number"))
      (is false (str (get w ":vw/id") " has an unknown :vw/number-source")))))

(deftest test-institution-level-only-g1
  ;; no per-individual field may ever appear — same enforcement shape as
  ;; test_directory's G1 test
  (doseq [w windows]
    (is (nil? (get w ":vw/officer-name")))
    (is (nil? (get w ":vw/badge-number")))
    (is (nil? (get w ":vw/direct-line")))
    (is (nil? (get w ":vw/staff-email")))))

;; ---------------------------------------------------------------------------
;; G11 — no-inbound-attestation. The load-bearing safety property of this wave.
;; ---------------------------------------------------------------------------

(deftest test-g11-no-inbound-number-field
  ;; the schema must not hold anything an attacker controls: a caller-id, an
  ;; allowlist of "numbers we consider genuine", a callback number supplied by
  ;; the caller. Matching against such a field would turn a spoofable input
  ;; into an attestation.
  (let [banned #"caller|inbound|allowlist|whitelist|known-good|trusted-number"]
    (doseq [w windows
            k (keys w)]
      (is (nil? (re-find banned (str/lower-case (str k))))
          (str (get w ":vw/id") " holds an attacker-controlled-looking key: " k)))))

(deftest test-g11-verdict-is-constant-and-never-affirms-the-caller
  ;; covered claim, uncovered claim, uncovered jurisdiction: the verdict is the
  ;; same constant every time. There is no input that makes this registry say
  ;; the other party is genuine.
  (doseq [[claim juris] [[":claim/police" ":jp"]
                         [":claim/bank" ":jp"]
                         [":claim/securities-firm" ":jp"]   ;; on the worklist
                         [":claim/police" ":br"]            ;; uncovered jurisdiction
                         [":claim/nonsense" ":jp"]]]
    (let [r (vw/lookup claim juris)]
      (is (= ":call-the-published-window" (get r "verdict"))
          (str claim "/" juris " must not produce a different verdict"))
      (is (not (str/includes? (str r) "genuine")))
      (is (not (str/includes? (str r) "legitimate"))))))

(deftest test-g11-uncovered-is-not-evidence-of-legitimacy
  ;; the honest degrade must SAY that an uncovered claim proves nothing --
  ;; silence here would read as "no window, so probably fine"
  (let [r (vw/lookup ":claim/tax-authority" ":jp")]
    (is (false? (get r "covered")))
    (is (str/includes? (get r "guidance") "根拠にはならない"))))

;; ---------------------------------------------------------------------------
;; lookup behaviour
;; ---------------------------------------------------------------------------

(deftest test-police-claim-resolves-to-npa-windows
  ;; the exact case this wave exists for: 「県警を名乗る着信」
  (let [r (vw/lookup ":claim/police" ":jp")
        ids (set (map #(get % "id") (get r "windows")))]
    (is (true? (get r "covered")))
    (is (contains? ids "vw:jp-police-consult"))
    (is (contains? ids "vw:jp-police-prefectural-list"))
    (is (str/includes? (get r "guidance") "自分から新規発信"))))

(deftest test-bank-claim-resolves-to-zenginkyo-windows
  (let [r (vw/lookup ":claim/bank" ":jp")
        ids (set (map #(get % "id") (get r "windows")))]
    (is (true? (get r "covered")))
    (is (contains? ids "vw:jp-zenginkyo-clinic-list"))))

(deftest test-court-claim-delegates-to-existing-legal-directory
  ;; :claim/court must NOT be duplicated into this registry -- it is answered by
  ;; legal-directory's :court-locator entries, so the two registries can never
  ;; drift apart on the same question
  (let [r (vw/lookup ":claim/court" ":jp")]
    (is (true? (get r "delegated_to_legal_directory")))
    (is (true? (get r "covered")))
    (is (not (some #{":claim/court"} (vw/claims-covered)))
        ":claim/court must not appear in the verification-window registry itself"))
  (let [r (vw/lookup ":claim/lawyer" ":us")]
    (is (true? (get r "delegated_to_legal_directory")))
    (is (true? (get r "covered")))))

(deftest test-uncovered-claim-degrades-empty
  (is (= [] (vw/windows-for-claim ":claim/securities-firm" ":jp"))))

(deftest test-worklist-names-the-gaps-and-stays-disjoint
  ;; every worklist item must be genuinely uncovered -- an entry that is both
  ;; "covered" and "on the worklist" would make the coverage report a lie
  (let [covered (set (vw/claims-covered))]
    (is (seq vw/claim-worklist))
    (doseq [c vw/claim-worklist]
      (is (not (contains? covered c)) (str c " is on the worklist but already covered")))))

(deftest test-coverage-counts-are-consistent
  (let [c (vw/coverage)]
    (is (= (get c "windows_total")
           (+ (get c "numbers_on_page") (get c "numbers_not_published_here"))))
    (is (= (get c "windows_total")
           (+ (get c "directly_fetched") (get c "linked_from_verified_page"))))
    (is (pos? (get c "directly_fetched")))
    (is (= [":jp"] (get c "jurisdictions"))
        "R0 wave is Japan-only -- the worklist, not a silent claim, carries the rest")))
