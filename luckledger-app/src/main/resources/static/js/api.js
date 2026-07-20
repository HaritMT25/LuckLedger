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
    function cookie(name) {
        const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return m ? decodeURIComponent(m[1]) : null;
    }

    /* CSRF (cookie-to-header): the server sets an XSRF-TOKEN cookie; mutating requests echo it in
       X-XSRF-TOKEN. Only the session-backed surface (auth + master) enforces it, but attaching the
       header everywhere is harmless. Prime with a cheap GET if the cookie hasn't been set yet. */
    async function csrfToken() {
        if (!cookie('XSRF-TOKEN')) await fetch('/api/health').catch(() => {});
        return cookie('XSRF-TOKEN');
    }

    async function request(method, path, body) {
        const opts = { method, headers: {} };
        if (method !== 'GET') {
            const token = await csrfToken();
            if (token) opts.headers['X-XSRF-TOKEN'] = token;
        }
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

    /* Login posts form fields (Spring Security's form login), not JSON. */
    async function login(username, password) {
        const token = await csrfToken();
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                ...(token ? { 'X-XSRF-TOKEN': token } : {}),
            },
            body: new URLSearchParams({ username, password }),
        });
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) throw new ApiError(res.status, data);
        return data;
    }

    return {
        // players
        createPlayer: (displayName) => request('POST', '/players', { displayName }),
        getPlayer: (id) => request('GET', `/players/${id}`),
        borrow: (id, amount) => request('POST', `/players/${id}/borrow`, { amount }),
        // catalogue
        dealers: () => request('GET', '/dealers'),
        rankings: () => request('GET', '/dealers/rankings'),
        dealer: (id) => request('GET', `/dealers/${id}`),
        dealerBooks: (id) => request('GET', `/dealers/${id}/books`),
        games: () => request('GET', '/games'),
        nearMisses: (gameId) => request('GET', `/games/${gameId}/near-misses`),
        mechanicDetail: (type) => request('GET', `/mechanics/${type}`),
        // auth & master
        login,
        logout: () => request('POST', '/auth/logout'),
        me: () => request('GET', '/auth/me'),
        house: () => request('GET', '/house/overview'),
        masterPlayers: () => request('GET', '/master/players'),
        grant: (playerId, amount) => request('POST', `/master/players/${playerId}/grant`, { amount }),
        restock: (gameId) => request('POST', `/master/games/${gameId}/restock`),
        // play
        purchase: (bookId, playerId) => request('POST', `/books/${bookId}/purchase`, { playerId }),
        ticket: (ticketId) => request('GET', `/tickets/${ticketId}`),
        reveal: (ticketId, playerId) => request('POST', `/tickets/${ticketId}/reveal`, { playerId }),
        pendingTickets: (playerId) => request('GET', `/players/${playerId}/tickets`),
        // ledger
        snapshot: (playerId) => request('GET', `/ledger/${playerId}`),
        transactions: (playerId, limit) => request('GET', `/ledger/${playerId}/transactions${limit ? `?limit=${limit}` : ''}`),
        insights: (playerId) => request('GET', `/ledger/${playerId}/insights`),
        curve: (playerId) => request('GET', `/ledger/${playerId}/curve`),
        dealerComparison: (playerId) => request('GET', `/ledger/${playerId}/dealer-comparison`),
    };
})();
