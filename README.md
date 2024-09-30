# PassRink インストール方法

## Windows 側の設定

1. **main ブランチを clone する**
   - GitHub のリポジトリから main ブランチをローカルに clone します。

2. **Google Chrome 拡張機能を登録する**
   - clone したフォルダの中から Google Chrome 拡張機能のフォルダを選択し、Chrome の拡張機能に登録します。
   - Chrome の拡張機能ページ (`chrome://extensions/`) で「パッケージ化されていない拡張機能を読み込む」から該当フォルダを選択してください。

3. **Google Chrome 拡張機能の ID を確認する**
   - 拡張機能ページで拡張機能の ID を確認して、メモしておきます。

4. **ネイティブアプリ (C#) の実行ファイルの絶対パスを確認**
   - `bluetoothplus.exe` の絶対パスを確認し、メモしておきます。
     例: `C:\path\to\bluetoothplus.exe`

5. **JSON ファイルを編集してレジストリファイルを作成**
   - `exam.json` ファイルを基に、以下の項目を設定します。
     - 実行ファイル (`bluetoothplus.exe`) の絶対パス
     - Google Chrome 拡張機能の ID
   - このファイルはレジストリ登録に必要です。

6. **レジストリにネイティブアプリの情報を登録**
   - Windows レジストリで Chrome のネイティブメッセージングホストのキーを作成します。
     - 名前は `com.example.bluetooth_bridge` にします。
     - レジストリの場所: `HKEY_CURRENT_USER\Software\Google\Chrome\NativeMessagingHosts\com.example.bluetooth_bridge`
   - `exam.reg` ファイルを参考にレジストリに登録してください。

7. **Windows 側で Bluetooth 接続を設定**
   - Windows の標準アプリ「スマートフォン連携」を使って Android スマートフォンと Bluetooth 連携を行い、通話機能を有効にしてください。
   - それ以外の方法でも Bluetooth 接続ができれば問題ありません。

---

## Android 側の設定

1. **APK ファイルをインストール**
   - Android デバイスに PassRink の APK ファイルをインストールします。
   - 必要に応じて設定メニューから外部ソースのアプリインストールを許可してください。
