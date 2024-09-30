// ログインフォームのIDとパスワード、送信時のURLを取得して保存する関数
function getLoginInfo(event) {
    let usernameInput = document.querySelector('input[type="text"], input[type="email"]');
    let passwordInput = document.querySelector('input[type="password"]');
    let currentUrl = window.location.href; // 現在のURLを取得

    // IDとパスワードが存在する場合のみ実行
    if (usernameInput && passwordInput) {
        let username = usernameInput.value;
        let password = passwordInput.value;

        if (username && password) {
            // ローカルストレージに保存
            chrome.storage.local.get({ credentials: [] }, function (result) {
                let credentials = result.credentials;

                // 資格情報を追加
                credentials.push({
                    url: currentUrl,
                    username: username,
                    password: password
                });

                // ローカルストレージに保存
                chrome.storage.local.set({ credentials: credentials }, function () {
                    console.log("URL、ID、パスワードが自動で保存されました。");
                });
            });
        }
    }
}

// フォーム送信時にURL、ID、パスワードを取得して処理を実行
document.addEventListener('submit', getLoginInfo);

document.addEventListener('click', (event) => {
    if (event.target.type === 'submit' || event.target.id === 'loginbutton') {
        getLoginInfo(event);  // ボタンがクリックされた時に実行
    }
});
