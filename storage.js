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
                const accounts = window.CbeStorage.getSavedAccounts();
                accounts.push({ name, number });
                window.safeStorage.setItem('savedAccounts', JSON.stringify(accounts));
            } catch(e) {
                console.error('saveAccount error:', e);
            }
        }
    };
})();
