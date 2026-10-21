import type { NextRequest } from "next/server";
import { signIn } from "@/lib/server/sign-in";

export function POST(request: NextRequest) {
  return signIn(request, "/api/auth/login");
}
