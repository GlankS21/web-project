import { NextResponse } from "next/server";

/** Liveness of the frontend server itself (used by the Docker health check). */
export function GET() {
  return NextResponse.json({ status: "UP" });
}
