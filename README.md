# meibo 名簿 — verified institution-window registry

**実在する機関の公式窓口への、検証済みリンク集。** 二つのレジストリを持つ:

| registry | data | 何に答えるか |
|---|---|---|
| `legal-directory` | `data/legal-directory.edn` — 10法域(日本・米国・英国・独国・韓国・仏国・豪州・加国・伊国・西国)、**23件** | 弁護士会・裁判所・(日本の)司法書士会/行政書士会 の公式検索窓口はどこか |
| `verification-window` | `data/verification-window.edn` — 日本、**8件** | 「Xを名乗る連絡があった」ときに、**X自身が公表している窓口**はどこか |

すべて実際にWeb検索・fetchで存在確認済み。個人の弁護士・裁判官・担当官の記録は
一切保持しない(機関レベルのリンクのみ、G1)。

- 📚 設計と不変条件: [`CLAUDE.md`](CLAUDE.md) · ADR-2607062200
- 🔗 これは gftdcojp ADR-0016 が計画しながら一度も実装されなかった
  judge/bengoshi/adr/legal-aid actor群の、誠実な代替実装です。詳細は ADR 参照。
- 🔗 姉妹actor: [`../saisei/`](../saisei/) (この`legal_directory`パターンの発祥元),
  [`../tate/`](../tate/) (30法域の`:juris/referrals`— 将来このactorのURLで補強予定)

## verification-window — なりすまし着信への折り返し検証 (2026-07-28 追加)

```clojure
(require '[meibo.methods.verification-window :as vw])
(vw/lookup ":claim/police" ":jp")
;; => {"verdict" ":call-the-published-window"
;;     "windows" [{"id" "vw:jp-police-consult" "published_number" "#9110" ...}
;;                {"id" "vw:jp-police-prefectural-list" ...}]
;;     "guidance" "着信の名乗り・発信者番号・折り返し先として相手が伝えた番号は一切使わない。…"}
```

対応している名乗り: `:claim/police` `:claim/police-investigation`
`:claim/fraud-investigation` `:claim/financial-regulator` `:claim/bank`
`:claim/bank-association` `:claim/consumer-affairs` `:claim/security-incident`。
`:claim/court` / `:claim/lawyer` は**重複させず** `legal-directory` 側へ委譲する
(同じ問いに二つのレジストリが食い違わないようにするため)。未対応の名乗りは
`claim-worklist` が名指しで宣言する(証券会社・税務当局・検察・通信事業者・
JPCERT/CC・執行官)。

**なぜ「判定器」ではなく「引き当て表」なのか。** 2026年の法人向けニセ警察詐欺
(はてな事案、11億7,981万円/約27時間)では、銀行の異常検知は送金ウィンドウの
**18.5%時点で既に発火していた**のに、その出力の終端先が「いま攻撃を受けている
本人」だったため実効ゲインが 0 になった。判断の質を上げる対策は、判断そのものが
攻撃対象になっている以上効かない。効くのは**判断を引き当てに置き換える**こと。
system-dynamics 側の計算は kotoba-lang/loop-system-dynamics の
`corporate-vishing-fraud` サイクル。

**Hard lines**: 機関レベルのみ(個人の弁護士・裁判官・担当官記録は保持しない、G1) ·
非裁定(「この人は良い弁護士」とは判断しない、G2) · 管轄正直(未収載法域・未収載の
名乗りは推測せず宣言する、G10) · **着信を肯定しない(G11 — 発信者番号を入力に取る
API を持たない。詳細は CLAUDE.md)**。

```bash
bb test
```

License: Apache 2.0 + etzhayyim Charter Compliance Rider (see repo root).
