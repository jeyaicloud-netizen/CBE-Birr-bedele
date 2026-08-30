// Shared storage functions
const CbeStorage = {
    getTransactions: () => JSON.parse(localStorage.getItem('cbe_transactions') || '[]'),
    saveTransaction: (transaction) => {
        const txns = CbeStorage.getTransactions();
        txns.push(transaction);
        localStorage.setItem('cbe_transactions', JSON.stringify(txns));
    },
    getBalance: () => parseFloat(localStorage.getItem('accountBalance') || '1000.00'),
    updateBalance: (amount) => {
        const balance = CbeStorage.getBalance();
        localStorage.setItem('accountBalance', (balance - amount).toFixed(2));
    },
    getSavedAccounts: () => JSON.parse(localStorage.getItem('savedAccounts') || '[]'),
    saveAccount: (name, number) => {
        const accounts = CbeStorage.getSavedAccounts();
        accounts.push({ name, number });
        localStorage.setItem('savedAccounts', JSON.stringify(accounts));
    }
};

window.CbeStorage = CbeStorage;
