package com.websarva.wings.android.bluetoothproto;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;  // リクエストコードを定義
    private static final String TAG = "BluetoothServer";
    private static final String APP_NAME = "BluetoothServerApp";
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66"); // 適切なUUIDを使ってください

    private BluetoothAdapter bluetoothAdapter;
    private AcceptThread acceptThread;
    private MyBluetoothService.ConnectedThread connectedThread; // クライアント接続スレッド
    private Handler handler;
    private TextView statusTextView, receivedMessageTextView;
    private Button startServerButton, sendMessageButton,restartServerButton;
    public int flag=0;
    public int approval=0;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // 各UIコンポーネントの参照を取得
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        statusTextView = findViewById(R.id.statusTextView);
        receivedMessageTextView = findViewById(R.id.receivedMessageTextView);
        startServerButton = findViewById(R.id.startServerButton);
        sendMessageButton = findViewById(R.id.sendMessageButton);
        restartServerButton = findViewById(R.id.restartServerButton);

        // パーミッションがない場合にリクエスト
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
        }

        // サーバー開始ボタンのクリックイベント
        startServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServer();
            }
        });

        // メッセージ送信ボタンのクリックイベント
        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connectedThread != null) {
                    String message = "Hello from Android!";
                    connectedThread.write(message.getBytes());  // クライアントにメッセージ送信
                } else {
                    Toast.makeText(MainActivity.this, "まだクライアントに接続されていません", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // サーバー再起動ボタンのクリックイベント
        restartServerButton.setOnClickListener(v -> restartServer());

        // 受信メッセージやエラーをUIに反映するためのHandler
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MyBluetoothService.MessageConstants.MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        String receivedMessage = new String(readBuf, 0, msg.arg1);
                        receivedMessageTextView.setText("Received: " + receivedMessage); // 受信メッセージを表示

                        if (receivedMessage.contains("http") && (flag == 1)) {
                            // ここでURL、ID、パスワードを保存する処理
                            saveUrlIdPass(receivedMessage);
                            flag = 0;
                        } else if (receivedMessage.equals("choose")) {
                            // ダイアログタイプ1を表示
                            showOkNoDialog(1, receivedMessage);
                        } else if (receivedMessage.contains("http") && (flag == 0)) {
                            // URLのチェックを行い、結果に応じてダイアログを表示
                            if (checkUrl(receivedMessage)) {
                                showOkNoDialog(2, receivedMessage);
                            } else {
                                // URLが無効な場合の処理（必要に応じて）
                            }
                        }
                        break;

                    case MyBluetoothService.MessageConstants.MESSAGE_TOAST:
                        Toast.makeText(MainActivity.this, msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });


        // ボタンのクリックイベント
        Button displayDataButton = findViewById(R.id.displayDataButton);
        displayDataButton.setOnClickListener(v -> displaySavedData());

        // 他のUI要素と同様に削除ボタンを取得
        Button deleteDataButton = findViewById(R.id.deleteDataButton);

        // 削除ボタンのクリックイベントリスナーを設定
        deleteDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 削除処理を呼び出す
                showDeleteDialog(); // ユーザーに確認ダイアログを表示
            }
        });


    }

    // サーバースレッドの開始
    private void startServer() {
        acceptThread = new AcceptThread();
        acceptThread.start();
        runOnUiThread(() -> statusTextView.setText("サーバーが開始しました..."));
    }

    // サーバーの再起動
    private void restartServer() {
        runOnUiThread(() -> statusTextView.setText("サーバーを再起動中..."));  // [修正] UIスレッドで更新
        // 2秒間テキストを保持する
        new Handler().postDelayed(() -> {
            stopServer();  // まずサーバーを停止
            startServer();  // 再度サーバーを開始
        }, 2000);  // 2000ミリ秒（2秒）後にサーバーを再起動
    }

    // サーバーの停止
    private void stopServer() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        runOnUiThread(() -> statusTextView.setText("サーバーが停止しました。"));  // [修正] UIスレッドで更新
    }


    // Bluetooth接続を待機するスレッド
    private class AcceptThread extends Thread {
        private  BluetoothServerSocket mmServerSocket=null;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "ソケットの作成に失敗しました", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (mmServerSocket == null) {
                // mmServerSocketがnullの場合、スレッドを終了する
                Log.e(TAG, "サーバーソケットが初期化されていないため、接続待機を中止します");
                return;
            }
            BluetoothSocket socket;
            while (true) {
                try {
                    // クライアント接続を待機
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "接続待ちでエラーが発生しました", e);
                    break;
                }

                if (socket != null) {
                    // 接続確立後にクライアントとの通信を開始
                    connectedThread = new MyBluetoothService.ConnectedThread(socket, handler);
                    connectedThread.start();

                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "サーバーソケットを閉じることができませんでした", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (mmServerSocket != null) {
                    mmServerSocket.close();  // サーバーソケットを閉じる
                }
            } catch (IOException e) {
                Log.e(TAG, "サーバーソケットを閉じることができませんでした", e);
            }
        }

    }

    // パーミッションリクエストの結果を処理する
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // パーミッションが許可された場合
                Log.d("MainActivity", "Bluetooth接続のパーミッションが許可されました");
            } else {
                // パーミッションが拒否された場合
                Log.d("MainActivity", "Bluetooth接続のパーミッションが拒否されました");
                Toast.makeText(this, "Bluetooth接続の権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showOkNoDialog(final int dialogType, final String receivedMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setCancelable(false); // キャンセル不可に設定

        switch (dialogType) {
            case 1:
                builder.setTitle("確認")
                        .setMessage("次のメッセージを受信しますか？")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String message = "OK";
                                connectedThread.write(message.getBytes()); // OKを送信
                                flag = 1; // フラグを更新
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String message = "NO";
                                connectedThread.write(message.getBytes()); // NOを送信
                            }
                        });
                break;

            case 2:


                if (checkUrl(receivedMessage)) {
                    // URLが存在した場合
                    builder.setTitle("確認")
                            .setMessage("ID、パスワードを送信しますか？")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // ファイルからIDとパスワードを読み込む
                                    String[] idPass = readIdAndPasswordFromFile(receivedMessage); // URLでIDとパスワードを取得

                                    if (idPass != null) {
                                        String id = idPass[0];
                                        String pass = idPass[1];
                                        String message = id + "," + pass;
                                        connectedThread.write(message.getBytes()); // OK,ID,PASSを送信
                                    } else {
                                        Toast.makeText(MainActivity.this, "IDとパスワードが取得できませんでした", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String message = "NO";
                                    connectedThread.write(message.getBytes()); // NOを送信
                                }
                            });
                } else {
                    // URLが存在しない場合
                    Toast.makeText(MainActivity.this, "指定されたURLは存在しません", Toast.LENGTH_SHORT).show();
                }
                break;
        }

        builder.show(); // ダイアログを表示
    }

    //url・id・passの保存
    private void saveUrlIdPass(String data) {
        // URL、ID、パスワードを保存する処理をここに記述
        List<String[]> savedData = readSavedData();
        String[] urlIdPass = data.split(",");
        String url = urlIdPass[0];
        String id = urlIdPass[1];
        String password = urlIdPass[2];

        if (isDuplicate(url, id, password, savedData)) {
            Toast.makeText(MainActivity.this, "既に同じデータが存在します", Toast.LENGTH_SHORT).show();
        } else if (isSameUrlDifferentPassword(url, id, password, savedData)) {
            showUpdateDialog(url, id, password, savedData);
        } else {
            saveNewData(url, id, password);
        }
    }
    //urlの検証
    private boolean checkUrl(String url) {
        try {
            FileInputStream fis = openFileInput("url_id_pass.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("URL: ")) {
                    String savedUrl = line.substring(5); // "URL: " の部分を除去
                    if (areStringsEqualUntilSecondDot(savedUrl, url)) {
                        fis.close(); // ファイルを閉じる
                        return true; // URLが存在する場合、trueを返す
                    }
                }
            }
            fis.close(); // ファイルを閉じる
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false; // URLが存在しない場合、falseを返す
    }


    private List<String[]> readSavedData() {
        List<String[]> dataList = new ArrayList<>();
        try {
            FileInputStream fis = openFileInput("url_id_pass.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;
            String url = null, id = null, password = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("URL: ")) {
                    url = line.substring(5);
                } else if (line.startsWith("ID: ")) {
                    id = line.substring(4);
                } else if (line.startsWith("Password: ")) {
                    password = line.substring(10);
                    if (url != null && id != null && password != null) {
                        dataList.add(new String[]{url, id, password});
                    }
                }
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dataList;
    }


    // ファイルが重複しているかどうか
    private boolean isDuplicate(String newUrl, String newId, String newPassword, List<String[]> savedData) {
        for (String[] data : savedData) {
            String url = data[0];
            String id = data[1];
            String password = data[2];
            if (areStringsEqualUntilSecondDot(url, newUrl)) { // 2つ目のドットまで一致するか確認
                if (id.equals(newId) && password.equals(newPassword)) {
                    // 完全に一致する場合
                    return true;
                } else if (id.equals(newId)) {
                    // URLとIDは一致、パスワードが異なる場合
                    return false;
                }
            }
        }
        return false;
    }
    //urlは一致するがidとpassが異なる場合
    private void showUpdateDialog(String url, String id, String newPassword, List<String[]> savedData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重複データ")
                .setMessage("URLは同じですが、パスワードが異なります。どうしますか？")
                .setPositiveButton("更新する", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // データを更新
                        updateData(url, id, newPassword, savedData);
                    }
                })
                .setNegativeButton("別として保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // 別に保存する処理
                        saveNewData(url, id, newPassword);
                    }
                })
                .show();
    }
    //データの更新
    private void updateData(String url, String id, String newPassword, List<String[]> savedData) {
        try {
            FileOutputStream fos = openFileOutput("url_id_pass.txt", Context.MODE_PRIVATE);
            for (String[] data : savedData) {
                if (data[0].equals(url) && data[1].equals(id)) {
                    // パスワードのみ更新
                    data[2] = newPassword;
                }
                String content = "URL: " + data[0] + "\nID: " + data[1] + "\nPassword: " + data[2] + "\n";
                fos.write(content.getBytes());
            }
            fos.close();
            Toast.makeText(MainActivity.this, "データが更新されました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "更新に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    // URL・IDは一致するがパスワードが違う場合を確認
    private boolean isSameUrlDifferentPassword(String newUrl, String newId, String newPassword, List<String[]> savedData) {
        for (String[] data : savedData) {
            String url = data[0];
            String id = data[1];
            String password = data[2];
            if (areStringsEqualUntilSecondDot(url, newUrl) && id.equals(newId) && !password.equals(newPassword)) {
                // URLとIDは一致するが、パスワードが異なる場合
                return true;
            }
        }
        return false;
    }
    //新しいデータの保存
    private void saveNewData(String url, String id, String password) {
        try {
            FileOutputStream fos = openFileOutput("url_id_pass.txt", Context.MODE_APPEND);
            String content = "URL: " + url + "\nID: " + id + "\nPassword: " + password + "\n";
            fos.write(content.getBytes());
            fos.close();
            Toast.makeText(MainActivity.this, "新しいデータが保存されました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //urlからidとpassを取ってくる
    private String[] readIdAndPasswordFromFile(String receivedUrl) {
        try {
            FileInputStream fis = openFileInput("url_id_pass.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("URL: ")) {
                    String savedUrl = line.substring(5); // "URL: "の部分を除去

                    // 2個目のコンマまでが一致するかどうか
                    if (areStringsEqualUntilSecondDot(savedUrl, receivedUrl)) {
                        // 一致したら、IDとパスワードを取得
                        String idLine = reader.readLine();
                        String passwordLine = reader.readLine();
                        if (idLine != null && passwordLine != null &&
                                idLine.startsWith("ID: ") && passwordLine.startsWith("Password: ")) {
                            String id = idLine.substring(4);
                            String pass = passwordLine.substring(10);
                            fis.close(); // ファイルを閉じる
                            return new String[]{id, pass}; // IDとパスワードを返す
                        }
                    }
                }
            }
            fis.close(); // ファイルを閉じる
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // 何も見つからなかった場合
    }

    // 2つ目のドットが出てくるまで文字列が同じかを判定するヘルパーメソッド
    private boolean areStringsEqualUntilSecondDot(String str1, String str2) {
        int dotCount = 0;
        int length = Math.min(str1.length(), str2.length());

        for (int i = 0; i < length; i++) {
            char c1 = str1.charAt(i);
            char c2 = str2.charAt(i);

            if (c1 != c2) {
                return false; // 文字が一致しない場合
            }

            if (c1 == '.') {
                dotCount++;
                if (dotCount == 2) {
                    return true; // 2つ目のドットまで一致
                }
            }
        }

        // ループが終わった後、ドットの数を確認
        if (dotCount < 2) {
            // どちらかの文字列が短くて2つ目のドットに到達しなかった場合
            return str1.length() == str2.length();
        }

        return true;
    }

    //保存されているデータの表示
    private void displaySavedData() {
        List<String[]> savedData = readSavedData();
        StringBuilder builder = new StringBuilder();

        for (String[] data : savedData) {
            builder.append("URL: ").append(data[0]).append("\n")
                    .append("ID: ").append(data[1]).append("\n")
                    .append("Password: ").append(data[2]).append("\n\n");
        }

        if (builder.length() > 0) {
            receivedMessageTextView.setText(builder.toString()); // データをTextViewに表示
        } else {
            receivedMessageTextView.setText("保存されたデータはありません。"); // データがない場合の表示
        }
    }

    // 削除の確認ダイアログを表示する
    private void showDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("データの削除")
                .setMessage("保存されているすべてのデータを削除しますか？")
                .setPositiveButton("削除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // データを削除する関数を呼び出す
                        deleteAllData();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    // データ削除処理
    private void deleteAllData() {
        // ファイルを削除する
        try {
            FileOutputStream fos = openFileOutput("url_id_pass.txt", Context.MODE_PRIVATE);
            fos.write("".getBytes()); // ファイルを空にする
            fos.close();
            Toast.makeText(MainActivity.this, "すべてのデータが削除されました", Toast.LENGTH_SHORT).show();

            // データ表示をクリア
            receivedMessageTextView.setText("保存されたデータはありません。");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "データの削除に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }




}