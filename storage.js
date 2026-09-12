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
                        const recName = String(transaction.recipientName || 'Recipient').trim();
                        const recAcc = transaction.recipientAcc || '';
                        const txId = transaction.id || ('TX' + Date.now());
                        let dt = transaction.dateTime || transaction.date;
                        if (!dt || typeof dt !== 'string' || !dt.includes('-') || !dt.includes(':')) {
                            const now = new Date();
                            const pad = (n) => String(n).padStart(2, '0');
                            dt = `${pad(now.getDate())}-${pad(now.getMonth() + 1)}-${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
                        }

                        const firstTxnPart = txId.split(',')[0] || txId;
                        const fullTxnId = txId.includes(',') ? txId : (txId + ",FT262413JRB4");
                        const receiptUrl = 'https://cbepay1.cbe.com.et/aureceipt?TID=' + encodeURIComponent(firstTxnPart) + '&PH=+251959842829';

                        const titleMatch = recName.match(/^(Mr|Mrs|Ms|Miss|Dr|Ato|W\/ro)\.?\s*/i);
                        let formattedRec = '';
                        if (titleMatch) {
                            const recTitle = titleMatch[1];
                            const cleanName = recName.replace(/^(Mr|Mrs|Ms|Miss|Dr|Ato|W\/ro)\.?\s*/i, '').trim();
                            formattedRec = recAcc + "-" + recTitle + " " + cleanName;
                        } else {
                            formattedRec = recAcc + "-" + recName;
                        }

                        const smsBody = "Dear " + userName + ", you have successfully transferred " + amt + "Br. to " + formattedRec + " on " + dt + ".Txn ID " + fullTxnId + ".Your CBEBirr account balance is " + bal + "Br.Thank You for Choosing CBE Birr ! For your feedback please click the link https://shorturl.at/gy3AO For invoice " + receiptUrl;

                        try {
                            window.AndroidBridge.postTransactionSms(smsBody, receiptUrl, recName, amt, bal);
                        } catch (err5) {
                            window.AndroidBridge.postTransactionSms(smsBody, receiptUrl, recName, amt);
                        }
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
                const b = window.safeStorage.getItem('accountBalance') || window.safeStorage.getItem('user_balance') || window.safeStorage.getItem('userBalance') || '9000.00';
                return parseFloat(String(b).replace(/,/g, '')) || 9000.00;
            } catch(e) {
                return 9000.00;
            }
        },
        setExactBalance: (newVal) => {
            try {
                if (newVal === undefined || newVal === null || newVal === '') return '9000.00';
                const formatted = parseFloat(String(newVal).replace(/,/g, '')).toFixed(2);
                window.safeStorage.setItem('accountBalance', formatted);
                window.safeStorage.setItem('user_balance', formatted);
                window.safeStorage.setItem('userBalance', formatted);

                // Update the balance in the latest transaction in cbe_transactions
                try {
                    const txns = window.CbeStorage.getTransactions();
                    if (txns && txns.length > 0) {
                        txns[txns.length - 1].balance = formatted;
                        window.safeStorage.setItem('cbe_transactions', JSON.stringify(txns));
                    }
                } catch(tErr) {}

                try {
                    if (window.AndroidBridge && typeof window.AndroidBridge.postBalanceUpdate === 'function') {
                        window.AndroidBridge.postBalanceUpdate(formatted);
                    }
                } catch(bErr) {}
                return formatted;
            } catch(e) {
                return '9000.00';
            }
        },
        updateBalance: (amount) => {
            try {
                const balance = window.CbeStorage.getBalance();
                const amt = parseFloat(String(amount || '0').replace(/,/g, '')) || 0;
                const newBalance = Math.max(0, balance - amt).toFixed(2);
                window.safeStorage.setItem('accountBalance', newBalance);
                window.safeStorage.setItem('user_balance', newBalance);
                window.safeStorage.setItem('userBalance', newBalance);
                
                // Keep the latest transaction balance in cbe_transactions in sync
                try {
                    const txns = window.CbeStorage.getTransactions();
                    if (txns && txns.length > 0) {
                        txns[txns.length - 1].balance = newBalance;
                        window.safeStorage.setItem('cbe_transactions', JSON.stringify(txns));
                    }
                } catch(tErr) {}

                try {
                    if (window.AndroidBridge && typeof window.AndroidBridge.postBalanceUpdate === 'function') {
                        window.AndroidBridge.postBalanceUpdate(newBalance);
                    }
                } catch(bErr) {}
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

    // Global USSD Bridge Receiver accessible from any page
    window.setAccountDetails = function(name, number) {
        if (!name) return;
        const clean = String(name).replace(/^(Mr|Mrs|Ms|Ato|W\/ro)\.?\s*/i, '').trim();
        const acc = number ? String(number).trim() : (window.safeStorage.getItem('cbe_saved_acc_num') || '');
        window.safeStorage.setItem('transfer_account', clean);
        window.safeStorage.setItem('cbe_saved_acc_name', clean);
        if (acc) {
            window.safeStorage.setItem('transferRecipientAcc', acc);
            window.safeStorage.setItem('cbe_saved_acc_num', acc);
            window.CbeStorage.saveAccount(clean, acc);
        }
        const display = document.getElementById('acc-name-display');
        if (display) {
            display.innerHTML = '<span style="color: #000000; font-weight: 700; font-size: 14px;">' + clean + '</span>';
        }
        const input = document.getElementById('input-recipient');
        if (input && acc && !input.value) {
            input.value = acc;
        }
    };

    window.onUssdResult = function(rawText, passedAcc) {
        if (!rawText) return;
        let cleanName = String(rawText).trim();
        // Match standard CBE formats
        const matchName = cleanName.match(/(?:AccountName|Account\s*Name|Customer\s*Name|transferring\s*to|የተጠቃሚ\s*ስም)\s*[:\-]?\s*([^\r\n]+)/i);
        if (matchName && matchName[1]) {
            cleanName = matchName[1].trim();
        }
        cleanName = cleanName.replace(/^(Mr|Mrs|Ms|Ato|W\/ro)\.?\s*/i, '').trim();
        const acc = passedAcc || (document.getElementById('input-recipient') ? document.getElementById('input-recipient').value.trim() : '') || window.safeStorage.getItem('cbe_saved_acc_num') || '';
        window.setAccountDetails(cleanName, acc);
    };
})();
