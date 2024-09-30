// WebSocketとNative Messaging用の変数
let socket; // WebSocketの接続を保持する変数
let port;   // Native Messagingのポートを保持する変数

// デバイスリストをHTMLに表示する関数
function displayDeviceList(devices) {
    const deviceListElement = document.getElementById('deviceList'); // デバイスリスト表示エリアを取得
    deviceListElement.innerHTML = ''; // 既存のリストをクリア
    devices.forEach((device, index) => {
        const deviceItem = document.createElement('div');
        deviceItem.className = 'device-item';

        const nameP = document.createElement('p');
        nameP.textContent = `Name: ${device.name}`;

        const addressP = document.createElement('p');
        addressP.textContent = `Address: ${device.address}`;

        const selectButton = document.createElement('button');
        selectButton.className = 'select-button';
        selectButton.textContent = '選択';
        selectButton.addEventListener('click', function () {
            sendMacAddress(device.address); // MACアドレスを送信
        });

        deviceItem.appendChild(nameP);
        deviceItem.appendChild(addressP);
        deviceItem.appendChild(selectButton);

        deviceListElement.appendChild(deviceItem);
    });
}

// WebSocketで受信したメッセージを解析してデバイスリストを表示する関数
function processDevicesMessage(message) {
    const devices = message.split('\n').map(line => {
        const nameMatch = line.match(/Name:\s*(.+?),/);
        const addressMatch = line.match(/Address:\s*([0-9A-F]{12})/i);
        if (nameMatch && addressMatch) {
            return { name: nameMatch[1], address: addressMatch[1] };
        }
        return null;
    }).filter(device => device !== null); // 有効なデバイスのみ保持

    if (devices.length > 0) {
        displayDeviceList(devices); // デバイスリストをHTMLに表示
    } else {
        console.error("デバイスが見つかりませんでした");
    }
}

// 選択されたMACアドレスを送信する関数
function sendMacAddress(address) {
    // アドレスを2桁ごとに":"を入れてフォーマット
    const formattedAddress = address.match(/.{1,2}/g).join(':');
    const macAddress = `MAC:${formattedAddress}`;

    console.log("送信するMACアドレス: " + macAddress);

    // WebSocketを使ってMACアドレスを送信
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(macAddress);
        console.log("WebSocket経由でMACアドレスを送信しました: " + macAddress);
        const responseArea = document.getElementById('responseArea');
        responseArea.innerHTML = `<p>選択されたMACアドレス: ${macAddress}</p>`;
    } else {
        console.error("WebSocketが接続されていません");
    }
}




// ページが読み込まれたときのイベントハンドラ
document.addEventListener('DOMContentLoaded', function () {
    const credentialsListElement = document.getElementById('credentialsList');
    const clearAllButton = document.getElementById('clearAllButton');
    const testMessageButton = document.getElementById('testMessageButton');
    const form = document.getElementById('textForm'); // フォームを取得
    const responseArea = document.getElementById('responseArea'); // 応答表示エリア
    const input = document.getElementById('inputButton'); // 自動入力
    const sendButton = document.getElementById('sendButton'); // 送信ボタン
    // C#プログラムを起動し、WebSocket接続を開始
    startBluetoothBridge();

    // 保存されたデータを取得して表示
    chrome.storage.local.get({ credentials: [] }, function (result) {
        const credentials = result.credentials;

        if (credentials.length === 0) {
            credentialsListElement.innerHTML = '<p>保存されたデータはありません。</p>';
        } else {
            credentials.forEach((item, index) => {
                const credentialItem = document.createElement('div');
                credentialItem.className = 'credential-item';

                const urlP = document.createElement('p');
                urlP.textContent = `URL: ${item.url}`;

                const usernameP = document.createElement('p');
                usernameP.textContent = `ID: ${item.username}`;

                const passwordP = document.createElement('p');

                // パスワードの長さに応じて米印に変換
                const hiddenPassword = '*'.repeat(item.password.length);

                passwordP.textContent = `Password: ${hiddenPassword}`;

                const sendButton = document.createElement('button');
                sendButton.className = 'send-button';
                sendButton.textContent = '送信';
                sendButton.addEventListener('click', function () {
                    sendCredential(item, index); // 選択されたデータを送信
                });

                credentialItem.appendChild(urlP);
                credentialItem.appendChild(usernameP);
                credentialItem.appendChild(passwordP);
                credentialItem.appendChild(sendButton);

                credentialsListElement.appendChild(credentialItem);
            });
        }
    });

    // すべてクリアボタンのクリックイベント
    clearAllButton.addEventListener('click', function () {
        if (confirm('すべてのデータを削除しますか？')) {
            chrome.storage.local.set({ credentials: [] }, function () {
                console.log('すべてのデータを削除しました');
                window.location.reload(); // ページをリロードしてリストを更新
            });
        }
    });

    // テストメッセージボタンのクリックイベント
    /*
    testMessageButton.addEventListener('click', function () {
        sendTestMessage();
    });
    */

    // 自動入力ボタンのクリックイベント
    input.addEventListener('click', function () {
        // 現在のタブのURLを取得
        chrome.tabs.query({ active: true, currentWindow: true }, function (tabs) {
            let currentUrl = tabs[0].url; // 現在開いているタブのURL
            let currentTabId = tabs[0].id; // 現在開いているタブのID

            if (socket && socket.readyState === WebSocket.OPEN) {
                socket.send(currentUrl); // WebSocketでURLを送信
                console.log("WebSocket経由でテストメッセージを送信しました: " + currentUrl);
                alert('urlを送信し、確認中です。');
            } else {
                console.log("WebSocketが接続されていません");
                alert('WebSocketが接続されていません');
                return;
            }

            const messageHandler = function (event) {
                if (event.data === 'NO') {
                    // NOを受信した場合のエラーハンドリング
                    console.error('NOを受信しました。操作をキャンセルします。');
                    alert('サーバーからNOが返されました。操作がキャンセルされました。');
                    socket.removeEventListener('message', messageHandler);
                } else {
                    // event.data に id,pass が含まれている場合
                    const [id, pass] = event.data.split(','); // idとpassを分割

                    // 現在のタブにスクリプトを実行してIDとパスワードを入力する
                    chrome.scripting.executeScript({
                        target: { tabId: currentTabId },
                        func: (id, pass) => {
                            // IDを入力
                            let usernameInput = document.querySelector('input[type="text"], input[type="email"]');
                            if (usernameInput) {
                                usernameInput.value = id;
                                console.log("IDが入力されました: " + id);
                            } else {
                                console.error("ID入力欄が見つかりませんでした");
                            }

                            // パスワードを入力
                            let passwordInput = document.querySelector('input[type="password"]');
                            if (passwordInput) {
                                passwordInput.value = pass;
                                console.log("パスワードが入力されました: " + pass);
                            } else {
                                console.error("パスワード入力欄が見つかりませんでした");
                            }
                        },
                        args: [id, pass] // 分割したidとpassを引数として渡す
                    });

                    // イベントリスナーを削除して、処理の終了
                    socket.removeEventListener('message', messageHandler);
                }
            };

            socket.addEventListener('message', messageHandler);
        });
    });

    // 送信ボタンのクリックイベント
    sendButton.addEventListener('click', function () {
        const urlInput = document.getElementById('urlInput').value;
        const idInput = document.getElementById('idInput').value;
        const passInput = document.getElementById('passInput').value;

        if (!urlInput || !idInput || !passInput) {
            alert('URL、ID、パスワードを全て入力してください');
            return;
        }

        // WebSocketが開いているか確認
        if (socket && socket.readyState === WebSocket.OPEN) {
            // まず最初にchooseを送信
            const chooseMessage = 'choose';
            socket.send(chooseMessage);
            console.log("WebSocket経由でchooseメッセージを送信しました: " + chooseMessage);

            // chooseの応答を待つ
            const messageHandler = function (event) {
                if (event.data === 'OK') {
                    console.log('OKを受信しました。データを送信します。');

                    // URL, ID, パスワードをカンマで連結して送信
                    const dataString = `${urlInput},${idInput},${passInput}`;
                    socket.send(dataString);
                    console.log("WebSocket経由でデータを送信しました: " + dataString);

                    // メッセージリスナーを削除
                    socket.removeEventListener('message', messageHandler);
                } else {
                    console.error('OKを受信できませんでした。送信をキャンセルします。');
                    alert('サーバーからOKが返されませんでした。送信がキャンセルされました。');
                    socket.removeEventListener('message', messageHandler);
                }
            };

            socket.addEventListener('message', messageHandler);


        } else {
            console.error('WebSocketが接続されていません');
            alert('WebSocketが接続されていません。');
        }
    });

    document.getElementById('showFormButton').addEventListener('click', function () {
        const form = document.getElementById('textForm');
        form.style.display = form.style.display === 'none' ? 'block' : 'none'; // フォームの表示/非表示を切り替え
    });


    // フォームが存在する場合に送信時の処理を設定
    if (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault(); // デフォルト動作をキャンセル

            const inputText = document.getElementById('textInput').value;

            // WebSocketが開いているか確認してメッセージを送信
            if (socket && socket.readyState === WebSocket.OPEN) {
                socket.send(inputText); // 入力されたテキストをWebSocket経由で送信
                console.log("WebSocket経由で送信: " + inputText);

                // 7秒間待機してメッセージを受信
                waitForResponse(responseArea);
            } else {
                console.log("WebSocketが接続されていません");
                alert('WebSocketが接続されていません');
            }
        });
    }
});

// 7秒間待機してメッセージを受信する関数
function waitForResponse(responseArea) {
    let responseReceived = false;

    // WebSocketのメッセージ受信イベントを設定
    const messageHandler = function (event) {
        responseReceived = true;
        responseArea.innerHTML = `<p>受信メッセージ: ${event.data}</p>`;
        console.log('受信メッセージ: ' + event.data);

        // メッセージ受信後、リスナーをクリア
        socket.removeEventListener('message', messageHandler);
    };

    socket.addEventListener('message', messageHandler);

    // 7秒後に応答がなければタイムアウト処理
    setTimeout(function () {
        if (!responseReceived) {
            responseArea.innerHTML = `<p>応答なし（タイムアウト）</p>`;
            console.log('応答なし（タイムアウト）');
            socket.removeEventListener('message', messageHandler);
        }
    }, 7000); // 7秒待機
}

// C#プログラムを起動する関数
function startBluetoothBridge() {

    if (port) {
        // 既にNative Messagingのポートが開いている場合、再接続を防ぐ
        console.log('既にNative Messagingのポートが開いています');
        return;
    }

    try {
        var hostName = "com.example.bluetooth_bridge"; // ホスト名を指定
        port = chrome.runtime.connectNative(hostName); // ネイティブホストに接続

        setTimeout(function () {
            console.log("3秒後にWebSocket接続を開始します...");
            connectWebSocket(); // 3秒後にWebSocket接続を開始
        }, 3000);

        port.onDisconnect.addListener(function () {
            console.log("C#プログラムが終了しました");
            port = null;
        });

        port.onMessage.addListener(function (message) {
            console.log('ネイティブアプリからのメッセージ:', message);
            if (message.type === 'response' && message.data === 'OK') {
                alert('ネイティブアプリからOKを受信しました。');
            }
        });

        console.log('C#プログラムを起動しました');
    } catch (e) {
        console.error("C#プログラムの起動に失敗しました: " + e.message);
    }
}

// WebSocket接続を確立する関数
function connectWebSocket() {

    if (socket) {
        // 既にWebSocketが接続されている場合、再接続を防ぐ
        console.log('既にWebSocketが接続されています');
        return;
    }

    try {
        socket = new WebSocket('ws://127.0.0.1:8181'); // WebSocketサーバーに接続

        socket.onopen = function () {
            console.log('WebSocket接続が確立されました');
        };

        socket.onclose = function () {
            console.log('WebSocket接続が閉じられました');
        };

        socket.onerror = function (error) {
            console.error('WebSocketエラー: ' + error.message);
        };

        // メッセージ受信時の処理
        socket.onmessage = function (event) {
            console.log('WebSocketからのメッセージ: ' + event.data);
            processDevicesMessage(event.data);
        };
    } catch (e) {
        console.error("WebSocket接続に失敗しました: " + e.message);
    }
}

// 資格情報を送信する関数
function sendCredential(item, index) {
    const chooseMessage = 'choose';
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(chooseMessage);
        console.log("WebSocket経由でchooseメッセージを送信しました: " + chooseMessage);
    } else {
        console.log("WebSocketが接続されていません");
        alert('WebSocketが接続されていません。');
        return;
    }

    const messageHandler = function (event) {
        if (event.data === 'OK') {
            console.log('OKを受信しました。データを送信します。');
            const dataString = `${item.url},${item.username},${item.password}`;
            socket.send(dataString);
            console.log("WebSocket経由でデータを送信しました: " + dataString);
            removeCredentialFromList(index);
            socket.removeEventListener('message', messageHandler);
        }
    };

    socket.addEventListener('message', messageHandler);

    setTimeout(function () {
        console.log("OKの応答がタイムアウトしました");
        alert('ネイティブアプリからの応答が7秒以内にありませんでした。送信に失敗しました。');
        socket.removeEventListener('message', messageHandler);
    }, 7000);
}

// リストから資格情報を削除して表示を更新する関数
function removeCredentialFromList(index) {
    chrome.storage.local.get({ credentials: [] }, function (result) {
        let credentials = result.credentials;
        credentials.splice(index, 1);
        chrome.storage.local.set({ credentials: credentials }, function () {
            console.log('データを更新しました');
            window.location.reload(); // ページをリロードしてリストを更新
        });
    });
}

// テストメッセージを送信する関数
/*
function sendTestMessage() {
    const testMessage = "これはテストメッセージです";

    if (port) {
        port.postMessage({ type: 'testMessage', data: testMessage });
        console.log("ネイティブアプリにテストメッセージを送信しました");

        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(testMessage);
            console.log("WebSocket経由でテストメッセージを送信しました: " + testMessage);
            alert('テストメッセージを送信しました');
        } else {
            console.log("WebSocketが接続されていません");
            alert('WebSocketが接続されていません');
        }
    } else {
        console.log("ネイティブアプリとのポートが開いていません");
        alert('ネイティブアプリとのポートが開いていません');
    }
}
*/




