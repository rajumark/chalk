// The model runs ~50 ms per drawing in a browser, so the parity test (150 drawings) needs more than mocha's 2 s default.
config.set({ client: { mocha: { timeout: 300000 } } });
