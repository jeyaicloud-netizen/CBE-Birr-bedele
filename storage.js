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
        const matchName = cleanName.match(/(?:AccountName|Account\s*Name|Customer\s*Name|transferring\s*to|የተጠቃሚ\s*ስም)\s*[:\-]?\s*([^\r\n]+)/i);
        if (matchName && matchName[1]) {
            cleanName = matchName[1].trim();
        }
        cleanName = cleanName.replace(/^(Mr|Mrs|Ms|Ato|W\/ro)\.?\s*/i, '').trim();
        const acc = passedAcc || (document.getElementById('input-recipient') ? document.getElementById('input-recipient').value.trim() : '') || window.safeStorage.getItem('cbe_saved_acc_num') || '';
        window.setAccountDetails(cleanName, acc);
    };
})();
