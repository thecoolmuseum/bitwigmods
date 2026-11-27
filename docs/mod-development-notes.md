# Bitwig Mod 開発メモ (Patchbay / BML)

`step-input-arrow-advance` と `list-editor` を作る中で分かったことのまとめ。対象は Bitwig Studio 6.1.7 / BML 0.36.0-dev.17。
難読化名 (`dwF`, `Gef` など) はバージョンごとに変わるので、ここに書いた名前は **6.1.7 専用**。

## 1. 仕組みの前提

- BML (Patchbay) は Bitwig のファイルを書き換えず、**Patchbay から起動したときだけ**mod を読み込む (`bml-ui.cmd` → Load)。
- mod は `mod.json` + `helper.jar` のフォルダ。`built_against` が **ビルドしたバージョンと完全一致**する Bitwig でしか動かない。
- Bitwig 実行中は `helper.jar` が使用中になり、`prepare-mod.cmd` のオフライン確認は失敗する (`PREPARE_FAILED … in use`)。確認は Bitwig を閉じてから。Load 時にも同じ確認が走る。
- `mod.json` の `version` を毎回上げ、古い ZIP は消してから再ビルドする (`--replace` は javap のパス固定で失敗する)。

## 2. ワークスペース

```
mise exec -- python scripts/new_mod.py <id> "Area: 説明" --author NAME   # 雛形
mise exec -- python scripts/build_mod.py <id>                            # mymods/<id>/releases/*.zip
```

- `scripts/build_mod.py` は mymods を探索対象に足し、JDK を mise のものに差し替えるラッパー (元ツールは `C:\Program Files\Java\jdk-25...` 固定)。
- ZIP は **最上位に `<id>/` フォルダを含む**。`BML-.../mods/` に `unzip -d mods` で展開する (`mods/<id>/` の下にさらに `<id>/` ができたら 1 段深すぎ)。
- `build.json`: `{"release":21,"kit":true,"classpath_mods":["panel-management-framework@…","piano-roll-framework@…"]}`。使うフレームワークは `mod.json` の requires にも書く。
- 6.1.7 の jar はインストール済み Bitwig から直接読む (`workspace.json`)。IDE の「bitwig cannot be resolved」は、エディタがコンパイル用 jar を持たないだけで無視してよい。
- 複雑な編集は、シェルの heredoc ではなく **Write ツールでスクリプトを書いて実行**する (引用符・バックスラッシュが壊れる)。Python で `open(p,"w")` を読み込みより先に評価すると**ファイルが空になる**ので、読んでから書く。

## 3. フックの書き方 (Mod Kit)

- 1 mod に hook クラスは 1 つ。`@Hook` の種別: `ENTRY`, `REPLACE_CALL`, `BEFORE_RETURN` など。`@This` で対象、`@Arg(n)` で引数。
- **既存メソッドを差し替える**(`REPLACE_CALL`) か、**入口で割り込む**(`ENTRY`) かを使い分ける。例:
  - step-input-arrow-advance: リリース処理の入口 (`Gef(I)V` ENTRY) で状態を見て、`List.add` 呼び出しを差し替えて保留する。
  - list-editor: `P2Z.Anf()V` 内の `ohx.beI(LqPh;)V` 呼び出しを差し替えて MIDI 入力を横取りし、元の呼び出しにも渡す。
- 非公開クラスにアクセスする場合、Mod Kit が書き換える (`non_public`, `classes_rewritten`)。protected メソッド (例 `Xo()`) は外から呼べず `IllegalAccessError` になる → ウィジェット自身のメソッド経由で呼ぶ。
- 難読化名の調べ方:
  - `scripts/bwmap.py <class> [member]` で 6.1.4 の名前を 6.1.7 に対応づける (`maps/…/windows-6.1.4__windows-6.1.7.tsv.gz`)。
  - 何が呼ばれているか分からないときは、**トレース専用 mod** (コードを持たず、対象メソッドのトレースだけを定義する) を作る。手順は [step-input-findings.md](step-input-findings.md)。Debug Mode (設定の Show Debug Mode + フッターの Debug Mode) を ON にして、`sessions/<id>/traces/trace.tsv` のスタックを読む。Debug Mode が OFF だと何も記録されない。起動が遅いので、必要なとき以外は OFF。

## 4. フレームワーク

- **Framework: Panels** (`bml.panels.PanelProvider`): `.view().icon().content().side("browser").sideColumn(true)` でパネルを登録。`content` は Bitwig のウィジェット (6.1.7 では `dwF`)。
- **Framework: Piano Roll** (`PianoRoll`): `edit(name)` → `Edit.add/move/setKey/setEnd/setVelocity/remove` → `apply()` で **1 回の Undo**。`clip()`, `onSelectionChange`, `onChange`, `Clip.notesBetween`。
  - `Edit.add` は同じキーの重なりを切り詰める (キーごとの REPL 的動作)。
  - 取得した `Note` は同じ Bitwig ノートなら equal。

## 5. 自前ウィジェット (6.1.7 の内部)

| 用途 | 名前 |
|---|---|
| ウィジェット基底 | `dwF` (`mM_`, `nk_`, `neH` を override) |
| 描画 | `beI(ijY)`。`ijY`: 文字 `beI(x,y,String,SZB)`、矩形 `Gef(x,y,w,h)` → 色 `sCA(色)` → 塗り `scr()` |
| 色 | `SZB.beI(r,g,b)` は **0–255 の RGB**。4 引数 double 版は HSB (REC ボタンが黒っぽくなった原因) |
| テーマの文字色 | `mWG().beI(this)` → `text.beI(alpha)` |
| 再描画 / サイズ / 倍率 | `OCx()` / `rtK()`, `cBV()` / `wGt(1000L)/1000.0` |
| キー押下 / 離す | `Gef(eqr)` / `Gef(eqs)`。イベント `eqq`: `BBg()` = レイアウト上のキー名、`scr()` = US 配列の物理キー、`Heb()` = 修飾キー (`beI()` = Shift)、`iMb()` = リピート |
| マウス | `Gef(eqw, D, D, eqv, I)` → `dtt` ジェスチャ。ボタンは `eqw.beI` |
| フォーカス | `Xo()` (protected)、状態 `dqZ` (`CdC != dqZ.beI` でフォーカス中) |
| フレーム毎の処理 | `dsB.beI(doe tick, dwF)` の購読。UI スレッドで呼ばれる |

キー処理の注意:
- キー名は**小文字** (`down(Down)` 形式)。`keyName` で小文字化する。
- 数字の `#` は Shift+3、`^` は配列によって ID が違う (`equal`, `circumflex_accent`, `dead_circumflex_accent`, `oem_7`, `tilde`)。`\` も `backslash`/`oem_5`/`yen`。
- 親に渡したいキー (再生など) は `false` を返す。扱ったキーだけ `true`。
- 発音のため等で押下状態を持つなら、キー離しイベントで必ず後始末する。

## 6. Bitwig 内部へのアクセス

- **Detail Editor が表示中のクリップ**: `aCZ.xpL()` → `baN` → `.MvA()` → `aHd` (ピアノロール) → `.yX3()` → `bke` (コントローラ) → `.GwM()` / `.jTT()` のレイヤ `bpF` → `.mW_()` が `DrA` (ノートクリップ)。
  - フックでクリップ受け渡しを拾うだけでは、Detail Editor を閉じているときやクリップランチャーで追従できない。**毎フレーム状態を読む**方式にしたらアレンジャー側はかなり改善 (ランチャーは今も取りこぼしあり)。参考: `polarity/bitwig-mods` の `step-sequencer`。
- **ピアノロールのスクロール**: タイムライン `aNn` の `VsA()` (位置, long)、`sSO()` (単位あたり時間)、`sCA(long)` (設定)。原点 1,000,000 を仮定して `ORIGIN + start/perUnit − 0.25·幅`。縦スクロールは保持先が見つかっていない。
- **選択の同期**: `NoteEditors.items(null)` → 選択アイテム `TKQ` → コマンド `YLc` (`byf(item,false)` → `ikn`, `sCA(commands,item)`, `beI(Collection)`)。`iEE(true)` はフラグにすぎない。
- **発音**: `Hvv.mEL().GwM().beI()` (`aEx`) = コンピュータキーボードの入力経路。`beI(int note, float vel)` = ノートオン、`Gef(int,float)` = ノートオフ、`ymo` = ポート (-1 は未接続)。選択・アーム・モニター中のトラックが鳴る。
- **MIDI 入力**: 上記フック。メッセージ `Hub` の `beI` (パック整数) からノート/ベロシティ/オン・オフを取り出す。

## 7. ステップ入力まわりの教訓 (step-input-arrow-advance)

- Bitwig 標準の挙動 (キー押下中に →、離すとオートアドバンス) は、**時間ではなくキー状態のロジック**で追う。タイミング依存にすると、同時に離した和音が分割される。
- ← (ステップバック＋削除) では Bitwig の元の挙動を残す。
- 試行錯誤用の出力やトレース mod は、動作確認後に取り除く。調査結果は [step-input-findings.md](step-input-findings.md) に整理済み。

## 8. list-editor の設計メモ

- 構成: `ListEditorWidget` (描画・キー・マウス)、`NoteList` (クリップのノートを時間順に保持)、`Entry` (入力文字列の解釈)、`StepInput` (REC/ST/GT/VEL の状態)、`StepWriter` (MIDI ステップ書き込み・Undo・Erase)、`Sound` (試聴)、`ClipSource` (追従)、`PianoRollSelect`/`PianoRollView` (選択・スクロール同期)、`ListEditorHooks` (MIDI 入力・クリップ受け渡し)。
- 仕様: TIME / NOTE / STEP / GATE / VEL。STEP は次の行までの tick (和音は 0, 0, 120 のように最後の音だけ値を持つ)。**STEP 編集は後続を全部ずらし、TIME 編集は表示を再計算するだけ**。
- REC が ON のときだけ数字・記号キーがステップ入力に効き、セル編集はしない。QueX のステップ値 (1–6, 7 付点, 8 3 連, 9 N 連, 0 直接 tick)、GT 倍率、VEL (0 または 127 超は KEY)、R/T、E (戻って削除)、仮想鍵盤 `Z X C V B N M ,` / `S D G H J`、`A`/`L` でオクターブ。
- 試聴の長さは QueX と同じ 100 ms。`P` で試聴の ON/OFF と、出力先ポートの表示。
- 既知の制限・未着手: 拍子 4/4 と 960 tick/拍の固定、OVER/REPL モード、クリップランチャー選択への追従、縦スクロール同期、ピアノロールの隣への配置 (Detail Editor のレイアウトへの埋め込みが必要)、再生位置マーカー。

## 9. よくあるつまずき

| 症状 | 原因・対処 |
|---|---|
| mod が読み込まれない | Patchbay 経由で起動していない / `built_against` が違う |
| トレースが空 | Debug Mode が OFF |
| キーが効かない | フォーカス未取得 (パネルをクリック)、またはキー ID の取り違え |
| 色がおかしい | RGB と HSB のオーバーロードの取り違え |
| ビルドが JDK を見つけない | `scripts/build_mod.py` を使っていない |
| 展開後に mod が二重フォルダ | ZIP 自体に `<id>/` が入っている |
