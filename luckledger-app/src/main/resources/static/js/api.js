/* Thin wrapper over the LuckLedger REST API. Every call returns parsed JSON or throws an
 * ApiError carrying the server's error envelope ({ message, code }). */

class ApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || `HTTP ${status}`);
        this.status = status;
        this.code = (body && body.code) || 'ERROR';
    }
}

const Api = (() => {
    async function request(method, path, body) {
        const opts = { method, headers: {} };
        if (body !== undefined) {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(`/api${path}`, opts);
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) {
            throw new ApiError(res.status, data);
        }
        return data;
    }

    return {
        // players
        createPlayer: (displayName) => request('POST', '/players', { displayName }),
        getPlayer: (id) => request('GET', `/players/${id}`),
        borrow: (id, amount) => request('POST', `/players/${id}/borrow`, { amount }),
        // catalogue
        dealers: () => request('GET', '/dealers'),
        books: () => request('GET', '/books'),
        games: () => request('GET', '/games'),
        // play
        purchase: (bookId, playerId) => request('POST', `/books/${bookId}/purchase`, { playerId }),
        ticket: (ticketId) => request('GET', `/tickets/${ticketId}`),
        reveal: (ticketId, playerId) => request('POST', `/tickets/${ticketId}/reveal`, { playerId }),
        // ledger
        snapshot: (playerId) => request('GET', `/ledger/${playerId}`),
        transactions: (playerId, limit) => request('GET', `/ledger/${playerId}/transactions${limit ? `?limit=${limit}` : ''}`),
        insights: (playerId) => request('GET', `/ledger/${playerId}/insights`),
    };
})();
