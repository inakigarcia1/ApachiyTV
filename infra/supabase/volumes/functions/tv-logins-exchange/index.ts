// Placeholder until tv-logins-exchange is fully implemented for Apachiy QR login.
Deno.serve((_req) =>
  new Response(
    JSON.stringify({
      error: "tv-logins-exchange not implemented yet",
      hint: "Use email/password signup for now",
    }),
    { status: 501, headers: { "Content-Type": "application/json" } },
  ),
);
