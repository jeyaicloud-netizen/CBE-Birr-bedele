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
                return parseFloat(window.safeStorage.getItem('accountBalance') || '1000.00');
            } catch(e) {
                return 1000.00;
            }
        },
        updateBalance: (amount) => {
            try {
                const balance = window.CbeStorage.getBalance();
                window.safeStorage.setItem('accountBalance', (balance - amount).toFixed(2));
            } catch(e) {
                console.error('updateBalance error:', e);
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
