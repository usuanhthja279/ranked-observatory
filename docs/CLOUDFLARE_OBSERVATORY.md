# Observatory public tunnel

Public URL verified on October 21, 2026:

https://tsunami-reserves-microwave-loan.trycloudflare.com/leaderboard.html

This is a Cloudflare Quick Tunnel to the separate Observatory service at 127.0.0.1:8090. It runs from this PC; it is not a serverless deployment. Keep the PC, Java service and cloudflared process running. A newly created tunnel gets a different hostname. Port 8080 is not used.

The official Cloudflare Windows binary is in build/tools/cloudflared.exe. Its downloaded SHA256 was 12c7c887f8aaab779aef1dfa9b60673530a5adb88ecc55dd6df8f56e0b70f199. Startup uses scripts/Start-LeaderboardTunnel.ps1, which reuses the existing process/URL when available. Tunnel PID, current base URL and logs are in build/cloudflare.pid, cloudflare.url and cloudflare.log. The tunnel starts in a hidden window and uses HTTP/2. Existing collection scheduling and administrator protection are retained.

The origin remains loopback-only. Native forwarded-header handling recognizes the tunnel's HTTPS scheme. Public verification confirmed HTTP 200, six featured RR values, routed country destinations, and a Secure session cookie without transmitting account credentials.

Cloudflare documentation: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

