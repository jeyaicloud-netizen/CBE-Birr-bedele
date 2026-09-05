// Shared storage functions with safe local + memory fallback for WebIntoApp / Vercel
(function() {
    window._cbeMemoryStore = window._cbeMemoryStore || {};
    window.safeStorage = window.safeStorage || {
        getItem: (key) => {
            try {
                return localStorage.getItem(key);
            } catch (e) {
                return window._cbeMemoryStore[key] || null;
            }
        },
        setItem: (key, val) => {
            try {
                localStorage.setItem(key, val);
            } catch (e) {
                window._cbeMemoryStore[key] = String(val);
            }
        },
        removeItem: (key) => {
            try {
                localStorage.removeItem(key);
            } catch (e) {
                delete window._cbeMemoryStore[key];
            }
        }
    };

    window.CbeStorage = window.CbeStorage || {
        getTransactions: () => {
            try {
                return JSON.parse(window.safeStorage.getItem('cbe_transactions') || '[]');
            } catch(e) {
                return [];
            }
        },
        saveTransaction: (transaction) => {
            try {
                const txns = window.CbeStorage.getTransactions();
                txns.push(transaction);
                window.safeStorage.setItem('cbe_transactions', JSON.stringify(txns));

                // Dispatch to Android Native System SMS & Notification
                try {
                    if (window.AndroidBridge && typeof window.AndroidBridge.postTransactionSms === 'function') {
                        const userName = "JUREYJ ABDUL MENAL HUSSEN";
                        const amt = parseFloat(transaction.amount || '0').toFixed(2);
                        const bal = parseFloat(transaction.balance || '1000.00').toFixed(2);
                        const recName = transaction.recipientName || 'Recipient';
                        const recAcc = transaction.recipientAcc || '';
                        const txId = transaction.id || ('TX' + Date.now());
                        const dt = transaction.dateTime || transaction.date || new Date().toLocaleString();

                        const receiptUrl = 'https://cbe-birr-bedele.vercel.app/receipt.html?amount=' + encodeURIComponent(amt) +
                            '&recipient=' + encodeURIComponent(recName) +
                            '&recipientAcc=' + encodeURIComponent(recAcc) +
                            '&txnId=' + encodeURIComponent(txId) +
                            '&dateTime=' + encodeURIComponent(dt);

                        const smsBody = "Dear " + userName + ", you have successfully transferred " + amt + "Br. to " + recAcc + " " + recName + " on " + dt + ".Txn ID " + txId + ".Your CBEBirr account balance is " + bal + "Br.Thank You for Choosing CBE Birr ! For invoice " + receiptUrl;

                        window.AndroidBridge.postTransactionSms(smsBody, receiptUrl, recName, amt);
                    }
                } catch(bridgeErr) {
                    console.warn('Bridge SMS dispatch error:', bridgeErr);
                }
            } catch(e) {
                console.error('saveTransaction error:', e);
            }
        },
        getBalance: () => {
            try {
                const b = window.safeStorage.getItem('accountBalance') || window.safeStorage.getItem('user_balance') || '1000.00';
                return parseFloat(b);
            } catch(e) {
                return 1000.00;
            }
        },
        updateBalance: (amount) => {
            try {
                const balance = window.CbeStorage.getBalance();
                const newBalance = Math.max(0, balance - parseFloat(amount || '0')).toFixed(2);
                window.safeStorage.setItem('accountBalance', newBalance);
                window.safeStorage.setItem('user_balance', newBalance);
                window.safeStorage.setItem('userBalance', newBalance);
                return newBalance;
            } catch(e) {
                console.error('updateBalance error:', e);
                return '0.00';
            }
        },
        getSavedAccounts: () => {
            try {
                return JSON.parse(window.safeStorage.getItem('savedAccounts') || '[]');
            } catch(e) {
                return [];
            }
        },
        saveAccount: (name, number) => {
            try {
                if (!name || !number) return;
                const accounts = window.CbeStorage.getSavedAccounts();
                const existingIdx = accounts.findIndex(a => a.number === String(number).trim());
                if (existingIdx >= 0) {
                    accounts[existingIdx].name = String(name).trim();
                } else {
                    accounts.push({ name: String(name).trim(), number: String(number).trim() });
                }
                window.safeStorage.setItem('savedAccounts', JSON.stringify(accounts));
                window.safeStorage.setItem('cbe_saved_acc_num', String(number).trim());
                window.safeStorage.setItem('cbe_saved_acc_name', String(name).trim());
            } catch(e) {
                console.error('saveAccount error:', e);
            }
        }
    };
})();
