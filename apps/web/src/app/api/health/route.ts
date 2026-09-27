export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const baseUrl = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";
    const response = await fetch(new URL("/api/v1/health", baseUrl), {
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
    });
    const data = await response.json();

    if (!response.ok || data.application !== "fieldops-api" || data.status !== "UP") {
      throw new Error("Unexpected backend health response");
    }

    return Response.json(
      { application: data.application, status: data.status },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch {
    return Response.json(
      { status: "UNAVAILABLE" },
      { status: 503, headers: { "Cache-Control": "no-store" } },
    );
  }
}
